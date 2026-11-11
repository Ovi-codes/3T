package ro.threet.run.registration;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;

/**
 * A run's public leaderboard (#44): everyone registered for it, ranked by their
 * self-entered finish time (#43). A read over existing rows — the {@code registration}
 * table was built in this shape (charter §3 seam).
 */
@Service
public class LeaderboardService {

	private final RegistrationRepository registrationRepository;

	private final EventRepository eventRepository;

	private final Clock clock;

	LeaderboardService(RegistrationRepository registrationRepository, EventRepository eventRepository, Clock clock) {
		this.registrationRepository = registrationRepository;
		this.eventRepository = eventRepository;
		this.clock = clock;
	}

	/**
	 * The ranked runners for one run that has taken place. The repository returns the
	 * rows in finishing order (fastest first, ties by who registered first, no time
	 * last); {@link Ranking} numbers the positions — a tied time shares its place (1, 2,
	 * 2, 4), and a runner without a time has no position.
	 *
	 * <p>
	 * Only a past, not-cancelled run has a leaderboard. That also keeps an upcoming run's
	 * list of registrants off this public endpoint.
	 * @throws RegistrationException 404 unknown run; 409 the run was cancelled or hasn't
	 * taken place
	 */
	@Transactional(readOnly = true)
	public LeaderboardResponse leaderboard(Long eventId) {
		Event event = eventRepository.findById(eventId)
			.orElseThrow(
					() -> new RegistrationException(HttpStatus.NOT_FOUND, "eventId", "That run could not be found."));
		if (event.isCancelled()) {
			throw new RegistrationException(HttpStatus.CONFLICT, "eventId",
					"This run was cancelled, so it has no results.");
		}
		// Same cutoff as the dashboard and finish-time entry: a run is past once its
		// start has gone by.
		if (!event.getStartDateTime().isBefore(OffsetDateTime.now(clock))) {
			throw new RegistrationException(HttpStatus.CONFLICT, "eventId",
					"This run hasn't taken place yet, so it has no results.");
		}

		var location = event.getLocation();
		return new LeaderboardResponse(event.getId(), event.getName(), event.getStartDateTime(), location.getName(),
				location.getCity(), rank(registrationRepository.findLeaderboard(eventId)));
	}

	private static List<LeaderboardEntry> rank(List<Registration> finishingOrder) {
		List<Integer> positions = Ranking.positions(finishingOrder);
		List<LeaderboardEntry> entries = new ArrayList<>(finishingOrder.size());
		for (int i = 0; i < finishingOrder.size(); i++) {
			Registration registration = finishingOrder.get(i);
			entries.add(new LeaderboardEntry(positions.get(i), PublicName.of(registration.getName()),
					registration.getFinishTimeSeconds()));
		}
		return entries;
	}

}
