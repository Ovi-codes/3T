package ro.threet.run.registration;

import java.time.OffsetDateTime;

/**
 * One finished run in the current user's personal stats (#45): which run it was, the
 * runner's own time, and where that time placed them.
 *
 * <p>
 * {@code position} is the runner's place on the run's leaderboard, ranked as in #44 (a
 * tied time shares its place). {@code finishers} is how many runners recorded a time on
 * that run, so the UI can say "2nd of 31".
 */
public record MyRunStat(Long eventId, String eventName, OffsetDateTime startDateTime, String locationName, String city,
		int finishTimeSeconds, int position, int finishers) {
}
