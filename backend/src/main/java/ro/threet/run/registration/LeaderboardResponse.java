package ro.threet.run.registration;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Body of {@code GET /api/events/{id}/leaderboard}: which run it is — name, when, where — and its
 * runners in finishing order (#44).
 */
public record LeaderboardResponse(
		Long eventId,
		String eventName,
		OffsetDateTime startDateTime,
		String locationName,
		String city,
		List<LeaderboardEntry> entries) {
}
