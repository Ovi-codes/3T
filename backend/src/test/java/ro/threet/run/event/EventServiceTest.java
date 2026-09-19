package ro.threet.run.event;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.threet.run.location.Location;
import ro.threet.run.location.LocationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The upcoming-events rule lives in the repository query name
 * ({@code StartDateTimeGreaterThanEqual...OrderByStartDateTimeAsc}); the service's job is to
 * feed it "now" from the injected clock and preserve the order into DTOs. This proves both
 * against a fixed instant. Real exclusion/ordering against data is proven by the integration
 * test on a live Postgres.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-21T09:00:00Z");
	private final Clock fixedClock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

	@Mock
	private EventRepository eventRepository;

	@Mock
	private LocationRepository locationRepository;

	@Captor
	private ArgumentCaptor<OffsetDateTime> cutoffCaptor;

	@Captor
	private ArgumentCaptor<Event> savedCaptor;

	private EventService service() {
		return new EventService(eventRepository, locationRepository, fixedClock);
	}

	@Test
	void queriesUpcomingFromTheClockNowAndMapsInOrder() {
		EventService service = service();
		Event soon = event(1L, "soonest", NOW.plusDays(1));
		Event later = event(2L, "next week", NOW.plusDays(8));
		given(eventRepository
				.findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(cutoffCaptor.capture()))
				.willReturn(List.of(soon, later));

		List<EventResponse> result = service.upcomingEvents();

		// the cutoff is exactly the clock's "now" — past events are asked to be excluded
		assertThat(cutoffCaptor.getValue()).isEqualTo(NOW);
		// order from the repository (ascending) is preserved into the DTOs
		assertThat(result).extracting(EventResponse::id).containsExactly(1L, 2L);
		assertThat(result).extracting(EventResponse::startDateTime).isSorted();
		assertThat(result.get(0)).isEqualTo(
				new EventResponse(1L, "soonest", NOW.plusDays(1), "Tineretului Park", "Bucharest"));
	}

	@Test
	void createInterpretsTheEnteredTimeAsBucharestAndBindsTheSoleLocation() {
		Location location = mock(Location.class);
		given(location.getName()).willReturn("Tineretului Park");
		given(location.getCity()).willReturn("Bucharest");
		given(locationRepository.findAll()).willReturn(List.of(location));
		given(eventRepository.save(any(Event.class))).willAnswer(call -> call.getArgument(0));

		// 21 Aug 2026 18:00 in Bucharest (EEST, UTC+3) → 15:00Z, comfortably after the fixed 09:00Z now.
		EventResponse response = service().createEvent(
				new CreateEventRequest("Tineretului parkrun", LocalDateTime.parse("2026-08-21T18:00")));

		verify(eventRepository).save(savedCaptor.capture());
		Event saved = savedCaptor.getValue();
		assertThat(saved.getName()).isEqualTo("Tineretului parkrun");
		assertThat(saved.getLocation()).isSameAs(location);
		// The entered wall-clock time keeps the Bucharest offset (+03:00 in August).
		assertThat(saved.getStartDateTime())
				.isEqualTo(OffsetDateTime.parse("2026-08-21T18:00+03:00"));
		assertThat(saved.getStartDateTime().toInstant())
				.isEqualTo(OffsetDateTime.parse("2026-08-21T15:00Z").toInstant());

		assertThat(response.name()).isEqualTo("Tineretului parkrun");
		assertThat(response.locationName()).isEqualTo("Tineretului Park");
		assertThat(response.city()).isEqualTo("Bucharest");
	}

	@Test
	void createRejectsAStartInThePastAndSavesNothing() {
		// 21 Aug 2026 10:00 in Bucharest → 07:00Z, before the fixed 09:00Z now.
		assertThatThrownBy(() -> service().createEvent(
				new CreateEventRequest("Past run", LocalDateTime.parse("2026-08-21T10:00"))))
				.isInstanceOf(EventValidationException.class)
				.satisfies(thrown -> assertThat(((EventValidationException) thrown).field())
						.isEqualTo("startDateTime"));

		verify(eventRepository, never()).save(any());
	}

	private Event event(Long id, String name, OffsetDateTime start) {
		Location location = mock(Location.class);
		given(location.getName()).willReturn("Tineretului Park");
		given(location.getCity()).willReturn("Bucharest");
		Event event = mock(Event.class);
		given(event.getId()).willReturn(id);
		given(event.getName()).willReturn(name);
		given(event.getStartDateTime()).willReturn(start);
		given(event.getLocation()).willReturn(location);
		return event;
	}

}