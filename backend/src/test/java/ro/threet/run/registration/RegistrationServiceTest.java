package ro.threet.run.registration;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

		// Mixed case + surrounding spaces to prove normalisation on the way in. Anonymous: no user.
		RegistrationResponse response = service()
				.register(new RegistrationRequest(1L, "  Ana  ", "  Ana@Example.com  "), null);

		verify(registrationRepository).save(savedCaptor.capture());
		Registration saved = savedCaptor.getValue();
		assertThat(saved.getName()).isEqualTo("Ana");
		assertThat(saved.getEmail()).isEqualTo("ana@example.com");
		assertThat(saved.getUserId()).isNull();

		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		verify(emailSender).send(eq("ana@example.com"), subject.capture(), anyString());
		assertThat(subject.getValue()).contains("Tineretului parkrun");

		assertThat(response.email()).isEqualTo("ana@example.com");
		assertThat(response.eventName()).isEqualTo("Tineretului parkrun");
	}

	@Test
	void attributesRegistrationToTheSignedInUser() {
		Event event = upcomingEvent();
		when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
		when(registrationRepository.existsByEventIdAndEmailIgnoreCase(1L, "ana@example.com")).thenReturn(false);
		when(registrationRepository.save(any(Registration.class))).thenAnswer(call -> call.getArgument(0));

		service().register(new RegistrationRequest(1L, "Ana", "ana@example.com"), 42L);

		verify(registrationRepository).save(savedCaptor.capture());
		assertThat(savedCaptor.getValue().getUserId()).isEqualTo(42L);
	}

	@Test
	void rejectsUnknownEventAndSendsNothing() {
		when(eventRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().register(new RegistrationRequest(99L, "Ana", "ana@example.com"), null))
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

		assertThatThrownBy(() -> service().register(new RegistrationRequest(1L, "Ana", "ana@example.com"), null))
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

		assertThatThrownBy(() -> service().register(new RegistrationRequest(1L, "Ana", "ana@example.com"), null))
				.isInstanceOf(RegistrationException.class)
				.satisfies(thrown -> assertThat(((RegistrationException) thrown).status())
						.isEqualTo(HttpStatus.CONFLICT));

		verify(registrationRepository, never()).save(any());
		verifyNoInteractions(emailSender);
	}

	@Test
	void splitsRegistrationsIntoUpcomingSoonestFirstAndPastMostRecentFirst() {
		// The repository returns rows newest-run-first (its `order by start_datetime desc`).
		Event future1 = event(10L, NOW.plusDays(3));
		Event future2 = event(11L, NOW.plusDays(10));
		Event past1 = event(20L, NOW.minusDays(14));
		Event past2 = event(21L, NOW.minusDays(7));
		when(registrationRepository.findByUserIdWithEvent(42L))
				.thenReturn(List.of(
						registration(future2), registration(future1),
						registration(past2), registration(past1)));

		MyRegistrationsResponse response = service().myRegistrations(42L);

		// Upcoming is flipped to soonest-first; past keeps the newest-first order.
		assertThat(response.upcoming()).extracting(MyRegistration::eventId).containsExactly(10L, 11L);
		assertThat(response.past()).extracting(MyRegistration::eventId).containsExactly(21L, 20L);
	}

	private Registration registration(Event event) {
		return new Registration(event, "Ana", "ana@example.com");
	}

	private Event upcomingEvent() {
		return event(1L, NOW.plusDays(3));
	}

	private Event event(OffsetDateTime start) {
		return event(1L, start);
	}

	private Event event(long id, OffsetDateTime start) {
		// Lenient: the reject cases throw before touching the event's details, so not every
		// stub is used on every path.
		Location location = mock(Location.class);
		lenient().when(location.getName()).thenReturn("Tineretului Park");
		lenient().when(location.getCity()).thenReturn("Bucharest");

		Event event = mock(Event.class);
		lenient().when(event.getId()).thenReturn(id);
		lenient().when(event.getName()).thenReturn("Tineretului parkrun");
		lenient().when(event.getStartDateTime()).thenReturn(start);
		lenient().when(event.getLocation()).thenReturn(location);
		return event;
	}

}