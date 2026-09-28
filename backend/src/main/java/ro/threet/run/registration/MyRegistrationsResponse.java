package ro.threet.run.registration;

import java.util.List;

/**
 * Body of {@code GET /api/me/registrations}: the current user's registrations split into the two
 * buckets the dashboard shows. "Upcoming" is runs starting now or later (soonest first); "past" is
 * runs whose start has gone by (most recent first). There is no attendance check — a past registration
 * simply means its event date has passed; each past row carries the runner's self-entered finish
 * time once they record one (#43).
 */
public record MyRegistrationsResponse(List<MyRegistration> upcoming, List<MyRegistration> past) {
}
