package ro.threet.run.registration;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ro.threet.run.email.EmailSender;
import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;
import ro.threet.run.location.Location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The registration rules in isolation, and — the point of the core loop's safety property — that
 * an email only goes out when a registration is actually recorded.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-21T09:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

	@Mock
	private RegistrationRepository registrationRepository;
	@Mock
	private EventRepository eventRepository;
	@Mock
	private EmailSender emailSender;

	@Captor
	private ArgumentCaptor<Registration> savedCaptor;

	private RegistrationService service() {
		return new RegistrationService(registrationRepository, eventRepository, emailSender, CLOCK);
	}

	@Test
	void recordsRegistrationAndSendsOneConfirmation() {
		Event event = upcomingEvent();
		when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
		when(registrationRepository.existsByEventIdAndEmailIgnoreCase(1L, "ana@example.com")).thenReturn(false);
		when(registrationRepository.save(any(Registration.class))).thenAnswer(call -> call.getArgument(0));

		// Mixed case + surrounding spaces to prove normalisation on the way in.
		RegistrationResponse response = service()
				.register(new RegistrationRequest(1L, "  Ana  ", "  Ana@Example.com  "));

		verify(registrationRepository).save(savedCaptor.capture());
		Registration saved = savedCaptor.getValue();
		assertThat(saved.getName()).isEqualTo("Ana");
		assertThat(saved.getEmail()).isEqualTo("ana@example.com");

		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		verify(emailSender).send(eq("ana@example.com"), subject.capture(), anyString());
		assertThat(subject.getValue()).contains("Tineretului parkrun");

		assertThat(response.email()).isEqualTo("ana@example.com");
		assertThat(response.eventName()).isEqualTo("Tineretului parkrun");
	}

	@Test
	void rejectsUnknownEventAndSendsNothing() {
		when(eventRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().register(new RegistrationRequest(99L, "Ana", "ana@example.com")))
				.isInstanceOf(RegistrationException.class)
				.satisfies(thrown -> assertThat(((RegistrationException) thrown).status())
						.isEqualTo(HttpStatus.NOT_FOUND));

		verify(registrationRepository, never()).save(any());
		verifyNoInteractions(emailSender);
	}

	@Test
	void rejectsPastEventAndSendsNothing() {
		Event past = event(NOW.minusDays(1));
		when(eventRepository.findById(1L)).thenReturn(Optional.of(past));

		assertThatThrownBy(() -> service().register(new RegistrationRequest(1L, "Ana", "ana@example.com")))
				.isInstanceOf(RegistrationException.class)
				.satisfies(thrown -> assertThat(((RegistrationException) thrown).status())
						.isEqualTo(HttpStatus.BAD_REQUEST));

		verify(registrationRepository, never()).save(any());
		verifyNoInteractions(emailSender);
	}

	@Test
	void rejectsDuplicateRegistrationAndSendsNothing() {
		Event event = upcomingEvent();
		when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
		when(registrationRepository.existsByEventIdAndEmailIgnoreCase(anyLong(), anyString())).thenReturn(true);

		assertThatThrownBy(() -> service().register(new RegistrationRequest(1L, "Ana", "ana@example.com")))
				.isInstanceOf(RegistrationException.class)
				.satisfies(thrown -> assertThat(((RegistrationException) thrown).status())
						.isEqualTo(HttpStatus.CONFLICT));

		verify(registrationRepository, never()).save(any());
		verifyNoInteractions(emailSender);
	}

	private Event upcomingEvent() {
		return event(NOW.plusDays(3));
	}

	private Event event(OffsetDateTime start) {
		// Lenient: the reject cases throw before touching the event's details, so not every
		// stub is used on every path.
		Location location = mock(Location.class);
		lenient().when(location.getName()).thenReturn("Tineretului Park");
		lenient().when(location.getCity()).thenReturn("Bucharest");

		Event event = mock(Event.class);
		lenient().when(event.getId()).thenReturn(1L);
		lenient().when(event.getName()).thenReturn("Tineretului parkrun");
		lenient().when(event.getStartDateTime()).thenReturn(start);
		lenient().when(event.getLocation()).thenReturn(location);
		return event;
	}

}