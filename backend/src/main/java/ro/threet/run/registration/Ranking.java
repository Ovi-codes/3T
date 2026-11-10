package ro.threet.run.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How finishing places are numbered on a run — shared by the public leaderboard (#44) and a runner's
 * personal stats (#45), so the two can never disagree.
 *
 * <p>Standard competition ranking: a tied time shares its place and the next runner skips ahead
 * (1, 2, 2, 4). A runner without a time has no place.
 */
final class Ranking {

	private Ranking() {
	}

	/**
	 * The place of each registration, index for index.
	 *
	 * @param finishingOrder one run's registrations, fastest first and runners without a time last
	 *                       — the order {@link RegistrationRepository#findLeaderboard} returns
	 * @return each runner's place, or null for a runner without a time
	 */
	static List<Integer> positions(List<Registration> finishingOrder) {
		List<Integer> positions = new ArrayList<>(finishingOrder.size());
		Integer previousTime = null;
		int position = 0;
		for (int i = 0; i < finishingOrder.size(); i++) {
			Integer time = finishingOrder.get(i).getFinishTimeSeconds();
			if (time != null && !Objects.equals(time, previousTime)) {
				position = i + 1;
			}
			previousTime = time;
			positions.add(time == null ? null : position);
		}
		return positions;
	}

}
