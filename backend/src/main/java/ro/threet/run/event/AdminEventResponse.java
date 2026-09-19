package ro.threet.run.event;

import java.time.OffsetDateTime;

/**
 * Body of the admin event reads ({@code GET /api/admin/events} and the edit/cancel replies). The
 * public {@link EventResponse} plus the two things only an admin may see: the event's
 * {@link EventStatus} and how many people are registered for it.
 *
 * <p>The count lives here and never on {@code EventResponse}, so the public payload carries no
 * registration figures (and no registrant details at all) — charter §7, data minimisation.
 */
public record AdminEventResponse(
		Long id,
		String name,
		OffsetDateTime startDateTime,
		String locationName,
		String city,
		EventStatus status,
		long registrationCount) {

	static AdminEventResponse from(Event event, long registrationCount) {
		return new AdminEventResponse(
				event.getId(),
				event.getName(),
				event.getStartDateTime(),
				event.getLocation().getName(),
				event.getLocation().getCity(),
				event.getStatus(),
				registrationCount);
	}

}
