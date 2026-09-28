package ro.threet.run.registration;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;
import ro.threet.run.location.Location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The leaderboard rules in isolation (#44). The repository hands back rows already in finishing
 * order (fastest first, no-time last, ties by registration); the service numbers the positions —
 * tied times share one — and decides which runs have a leaderboard at all.
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-21T09:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

	@Mock
	private RegistrationRepository registrationRepository;
	@Mock
	private EventRepository eventRepository;

	private LeaderboardService service() {
		return new LeaderboardService(registrationRepository, eventRepository, CLOCK);
	}

	@Test
	void ranksRunnersInFinishingOrderWithTheRunsDetails() {
		Event event = pastEvent();
		when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
		when(registrationRepository.findLeaderboard(1L)).thenReturn(List.of(
				runner(event, "Ana Pop", 1300),
				runner(event, "Bogdan Ionescu", 1450),
				runner(event, "Carmen Dumitru", 1610)));

		LeaderboardResponse leaderboard = service().leaderboard(1L);

		assertThat(leaderboard.eventId()).isEqualTo(1L);
		assertThat(leaderboard.eventName()).isEqualTo("Tineretului parkrun");
		assertThat(leaderboard.startDateTime()).isEqualTo(NOW.minusDays(7));
		assertThat(leaderboard.locationName()).isEqualTo("Tineretului Park");
		assertThat(leaderboard.city()).isEqualTo("Bucharest");
		assertThat(leaderboard.entries())
				.extracting(LeaderboardEntry::position, LeaderboardEntry::runnerName,
						LeaderboardEntry::finishTimeSeconds)
				.containsExactly(
						tuple(1, "Ana P.", 1300),
						tuple(2, "Bogdan I.", 1450),
						tuple(3, "Carmen D.", 1610));
	}

	@Test
	void tiedTimesShareAPositionAndTheNextRunnerSkipsAhead() {
		Event event = pastEvent();
		when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
		when(registrationRepository.findLeaderboard(1L)).thenReturn(List.of(
				runner(event, "Ana Pop", 1300),
				runner(event, "Bogdan Ionescu", 1450),
				runner(event, "Carmen Dumitru", 1450),
				runner(event, "Dan Georgescu", 1610)));

		// Standard competition ranking: 1, 2, 2, 4.
		assertThat(service().leaderboard(1L).entries())
				.extracting(LeaderboardEntry::runnerName, LeaderboardEntry::position)
				.containsExactly(
						tuple("Ana P.", 1),
						tuple("Bogdan I.", 2),
						tuple("Carmen D.", 2),
						tuple("Dan G.", 4));
	}

	@Test
	void runnersWithoutATimeComeLastWithNoPosition() {
		Event event = pastEvent();
		when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
		when(registrationRepository.findLeaderboard(1L)).thenReturn(List.of(
				runner(event, "Ana Pop", 1300),
				runner(event, "Elena Stan", null),
				runner(event, "Florin Matei", null)));

		assertThat(service().leaderboard(1L).entries())
				.extracting(LeaderboardEntry::runnerName, LeaderboardEntry::position,
						LeaderboardEntry::finishTimeSeconds)
				.containsExactly(
						tuple("Ana P.", 1, 1300),
						tuple("Elena S.", null, null),
						tuple("Florin M.", null, null));
	}

	@Test
	void aPastRunWithNobodyRegisteredHasAnEmptyLeaderboard() {
		Event event = pastEvent();
		when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
		when(registrationRepository.findLeaderboard(1L)).thenReturn(List.of());

		assertThat(service().leaderboard(1L).entries()).isEmpty();
	}

	@Test
	void anUnknownRunIsNotFound() {
		when(eventRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().leaderboard(99L))
				.isInstanceOfSatisfying(RegistrationException.class,
						error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
		verifyNoInteractions(registrationRepository);
	}

	@Test
	void aRunThatHasNotTakenPlaceHasNoLeaderboard() {
		// Also keeps an upcoming run's registrant list off the public API.
		Event upcoming = event(NOW.plusDays(3), false);
		when(eventRepository.findById(1L)).thenReturn(Optional.of(upcoming));

		assertThatThrownBy(() -> service().leaderboard(1L))
				.isInstanceOfSatisfying(RegistrationException.class,
						error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
		verifyNoInteractions(registrationRepository);
	}

	@Test
	void aCancelledRunHasNoLeaderboard() {
		Event cancelled = event(NOW.minusDays(7), true);
		when(eventRepository.findById(1L)).thenReturn(Optional.of(cancelled));

		assertThatThrownBy(() -> service().leaderboard(1L))
				.isInstanceOfSatisfying(RegistrationException.class,
						error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
		verifyNoInteractions(registrationRepository);
	}

	private static Registration runner(Event event, String name, Integer finishTimeSeconds) {
		Registration registration = new Registration(event, name, name.replace(' ', '.') + "@example.com");
		if (finishTimeSeconds != null) {
			registration.recordFinishTime(Duration.ofSeconds(finishTimeSeconds));
		}
		return registration;
	}

	private static Event pastEvent() {
		return event(NOW.minusDays(7), false);
	}

	private static Event event(OffsetDateTime start, boolean cancelled) {
		// Lenient: the reject cases throw before reading the run's details.
		Location location = mock(Location.class);
		lenient().when(location.getName()).thenReturn("Tineretului Park");
		lenient().when(location.getCity()).thenReturn("Bucharest");

		Event event = mock(Event.class);
		lenient().when(event.getId()).thenReturn(1L);
		lenient().when(event.getName()).thenReturn("Tineretului parkrun");
		lenient().when(event.getStartDateTime()).thenReturn(start);
		lenient().when(event.isCancelled()).thenReturn(cancelled);
		lenient().when(event.getLocation()).thenReturn(location);
		return event;
	}

}
