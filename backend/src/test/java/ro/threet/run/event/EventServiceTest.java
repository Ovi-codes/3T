package ro.threet.run.event;

import java.time.Clock;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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

	@Captor
	private ArgumentCaptor<OffsetDateTime> cutoffCaptor;

	@Test
	void queriesUpcomingFromTheClockNowAndMapsInOrder() {
		EventService service = new EventService(eventRepository, fixedClock);
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