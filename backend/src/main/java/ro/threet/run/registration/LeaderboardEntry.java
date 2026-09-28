package ro.threet.run.registration;

/**
 * One row of a run's public leaderboard (#44).
 *
 * <p>{@code position} is the finishing place — runners on the same time share one, and the next
 * place skips ahead (1, 2, 2, 4). It is null, like {@code finishTimeSeconds}, for a runner who
 * hasn't entered a time; those rows come last. {@code runnerName} is the shortened public form
 * ({@link PublicName}), never the full name or the email.
 */
public record LeaderboardEntry(Integer position, String runnerName, Integer finishTimeSeconds) {
}
