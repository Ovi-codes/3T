package ro.threet.run.registration;

import java.util.List;

/**
 * Body of {@code GET /api/me/registrations}: the current user's registrations split into the two
 * buckets the dashboard shows. "Upcoming" is runs starting now or later (soonest first); "past" is
 * runs whose start has gone by (most recent first). V1 has no attendance check — a past registration
 * simply means its event date has passed (charter §3 seam; {@code finish_time} arrives in V2).
 */
public record MyRegistrationsResponse(List<MyRegistration> upcoming, List<MyRegistration> past) {
}
