package ro.threet.run.registration;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ro.threet.run.event.Event;
import ro.threet.run.location.Location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The personal-stats rules in isolation (#45). The caller's finished runs — past, not cancelled,
 * with a recorded time — come back oldest first so they plot as a time trend, each with the
 * caller's place on that run's leaderboard (the same ranking as #44: a tied time shares its place)
 * and how many runners recorded a time there.
 */
@ExtendWith(MockitoExtension.class)
class MyStatsServiceTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-21T09:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
	private static final long ANA = 7L;

	@Mock
	private RegistrationRepository registrationRepository;

	private long nextRegistrationId = 100;

	private MyStatsService service() {
		return new MyStatsService(registrationRepository, CLOCK);
	}

	@Test
	void givesPlacementAndTimeForEachFinishedRunOldestFirst() {
		Event older = event(1L, "Tineretului parkrun", NOW.minusDays(14), false);
		Event newer = event(2L, "Herăstrău parkrun", NOW.minusDays(7), false);
		Registration anaOlder = registration(older, "Ana Pop", ANA, 1500);
		Registration anaNewer = registration(newer, "Ana Pop", ANA, 1420);
		// The dashboard query hands rows back newest run first.
		when(registrationRepository.findByUserIdWithEvent(ANA)).thenReturn(List.of(anaNewer, anaOlder));
		when(registrationRepository.findLeaderboards(anyCollection())).thenReturn(List.of(
				registration(older, "Bogdan Ionescu", null, 1300),
				anaOlder,
				registration(older, "Carmen Dumitru", null, 1610),
				anaNewer,
				registration(newer, "Bogdan Ionescu", null, 1450)));

		MyStatsResponse stats = service().stats(ANA);

		assertThat(stats.runs())
				.extracting(MyRunStat::eventId, MyRunStat::eventName, MyRunStat::startDateTime,
						MyRunStat::finishTimeSeconds, MyRunStat::position, MyRunStat::finishers)
				.containsExactly(
						tuple(1L, "Tineretului parkrun", NOW.minusDays(14), 1500, 2, 3),
						tuple(2L, "Herăstrău parkrun", NOW.minusDays(7), 1420, 1, 2));
		assertThat(stats.runs().getFirst().locationName()).isEqualTo("Tineretului Park");
		assertThat(stats.runs().getFirst().city()).isEqualTo("Bucharest");
	}

	@Test
	void aTiedTimeSharesThePlaceOfTheRunnerAhead() {
		Event run = event(1L, "Tineretului parkrun", NOW.minusDays(7), false);
		Registration bogdan = registration(run, "Bogdan Ionescu", null, 1450);
		Registration ana = registration(run, "Ana Pop", ANA, 1450);
		when(registrationRepository.findByUserIdWithEvent(ANA)).thenReturn(List.of(ana));
		// Bogdan registered first, so he is listed ahead — but on the same time Ana shares his place.
		when(registrationRepository.findLeaderboards(anyCollection())).thenReturn(List.of(
				registration(run, "Carmen Dumitru", null, 1300),
				bogdan,
				ana,
				registration(run, "Dan Georgescu", null, 1610)));

		assertThat(service().stats(ANA).runs())
				.extracting(MyRunStat::position, MyRunStat::finishers)
				.containsExactly(tuple(2, 4));
	}

	@Test
	void runnersWithoutATimeDoNotCountAsFinishers() {
		Event run = event(1L, "Tineretului parkrun", NOW.minusDays(7), false);
		Registration ana = registration(run, "Ana Pop", ANA, 1450);
		when(registrationRepository.findByUserIdWithEvent(ANA)).thenReturn(List.of(ana));
		when(registrationRepository.findLeaderboards(anyCollection())).thenReturn(List.of(
				ana,
				registration(run, "Elena Stan", null, null),
				registration(run, "Florin Matei", null, null)));

		assertThat(service().stats(ANA).runs())
				.extracting(MyRunStat::position, MyRunStat::finishers)
				.containsExactly(tuple(1, 1));
	}

	@Test
	void leavesOutRunsWithoutATimeUpcomingRunsAndCancelledRuns() {
		Event finished = event(1L, "Finished", NOW.minusDays(21), false);
		Event noTime = event(2L, "No time", NOW.minusDays(14), false);
		Event cancelled = event(3L, "Cancelled", NOW.minusDays(7), true);
		Event upcoming = event(4L, "Upcoming", NOW.plusDays(3), false);
		Registration anaFinished = registration(finished, "Ana Pop", ANA, 1500);
		when(registrationRepository.findByUserIdWithEvent(ANA)).thenReturn(List.of(
				registration(upcoming, "Ana Pop", ANA, null),
				registration(cancelled, "Ana Pop", ANA, 1400),
				registration(noTime, "Ana Pop", ANA, null),
				anaFinished));
		when(registrationRepository.findLeaderboards(anyCollection())).thenReturn(List.of(anaFinished));

		assertThat(service().stats(ANA).runs())
				.extracting(MyRunStat::eventId)
				.containsExactly(1L);
		// Only the finished run's leaderboard is read.
		verify(registrationRepository).findLeaderboards(argThat(ids -> ids.size() == 1 && ids.contains(1L)));
	}

	@Test
	void aUserWithNoFinishedRunsHasEmptyStats() {
		Event noTime = event(1L, "No time", NOW.minusDays(7), false);
		when(registrationRepository.findByUserIdWithEvent(ANA))
				.thenReturn(List.of(registration(noTime, "Ana Pop", ANA, null)));

		assertThat(service().stats(ANA).runs()).isEmpty();
		// Nothing to rank, so no leaderboard read at all.
		verify(registrationRepository, never()).findLeaderboards(anyCollection());
	}

	@Test
	void aUserWithNoRegistrationsHasEmptyStats() {
		when(registrationRepository.findByUserIdWithEvent(ANA)).thenReturn(new ArrayList<>());

		assertThat(service().stats(ANA).runs()).isEmpty();
		verify(registrationRepository, never()).findLeaderboards(anyCollection());
	}

	private Registration registration(Event event, String name, Long userId, Integer finishTimeSeconds) {
		Registration registration = new Registration(event, name, name.replace(' ', '.') + "@example.com");
		// Saved rows have ids; the service tells the caller's row apart from the rest of the field by it.
		ReflectionTestUtils.setField(registration, "id", nextRegistrationId++);
		if (userId != null) {
			registration.linkUser(userId);
		}
		if (finishTimeSeconds != null) {
			registration.recordFinishTime(Duration.ofSeconds(finishTimeSeconds));
		}
		return registration;
	}

	private static Event event(Long id, String name, OffsetDateTime start, boolean cancelled) {
		// Lenient: runs that are left out of the stats never have their details read.
		Location location = mock(Location.class);
		lenient().when(location.getName()).thenReturn("Tineretului Park");
		lenient().when(location.getCity()).thenReturn("Bucharest");

		Event event = mock(Event.class);
		lenient().when(event.getId()).thenReturn(id);
		lenient().when(event.getName()).thenReturn(name);
		lenient().when(event.getStartDateTime()).thenReturn(start);
		lenient().when(event.isCancelled()).thenReturn(cancelled);
		lenient().when(event.getLocation()).thenReturn(location);
		return event;
	}

}
