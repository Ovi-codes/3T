package ro.threet.run.registration;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A signed-in runner's personal stats (#45): their time and place on each run they finished. Reads
 * the finish times of #43 and ranks them exactly like the public leaderboard of #44
 * ({@link Ranking}).
 */
@Service
public class MyStatsService {

	private final RegistrationRepository registrationRepository;
	private final Clock clock;

	MyStatsService(RegistrationRepository registrationRepository, Clock clock) {
		this.registrationRepository = registrationRepository;
		this.clock = clock;
	}

	/**
	 * The user's finished runs, oldest first. A run counts when it has taken place (the dashboard's
	 * cutoff — its start has gone by), was not cancelled, and the user recorded a time on it — the
	 * same runs that have a leaderboard with the user's place on it.
	 *
	 * @param userId the current account (never null — the endpoint requires authentication)
	 */
	@Transactional(readOnly = true)
	public MyStatsResponse stats(Long userId) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		// Rows arrive newest run first; the trend reads oldest first.
		List<Registration> finished = registrationRepository.findByUserIdWithEvent(userId).stream()
				.filter(registration -> registration.getEvent().getStartDateTime().isBefore(now))
				.filter(registration -> !registration.getEvent().isCancelled())
				.filter(registration -> registration.getFinishTime() != null)
				.toList()
				.reversed();
		if (finished.isEmpty()) {
			return new MyStatsResponse(List.of());
		}

		Map<Long, List<Registration>> leaderboards = registrationRepository
				.findLeaderboards(finished.stream().map(registration -> registration.getEvent().getId()).toList())
				.stream()
				.collect(Collectors.groupingBy(registration -> registration.getEvent().getId()));

		return new MyStatsResponse(finished.stream()
				.map(mine -> stat(mine, leaderboards.getOrDefault(mine.getEvent().getId(), List.of(mine))))
				.toList());
	}

	private static MyRunStat stat(Registration mine, List<Registration> finishingOrder) {
		List<Integer> positions = Ranking.positions(finishingOrder);
		int position = 0;
		int finishers = 0;
		for (int i = 0; i < finishingOrder.size(); i++) {
			if (positions.get(i) != null) {
				finishers++;
			}
			if (Objects.equals(finishingOrder.get(i).getId(), mine.getId())) {
				position = positions.get(i);
			}
		}
		var event = mine.getEvent();
		var location = event.getLocation();
		return new MyRunStat(event.getId(), event.getName(), event.getStartDateTime(), location.getName(),
				location.getCity(), mine.getFinishTimeSeconds(), position, finishers);
	}

}
