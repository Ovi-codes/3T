package ro.threet.run.registration;

import java.util.List;

/**
 * Body of {@code GET /api/me/stats} (#45): the current user's finished runs — past, not cancelled,
 * with a recorded time — oldest first, so they read left to right as a time trend. Empty when the
 * user has no finished runs yet.
 */
public record MyStatsResponse(List<MyRunStat> runs) {
}
