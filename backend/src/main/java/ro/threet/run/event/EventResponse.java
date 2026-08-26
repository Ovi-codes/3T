package ro.threet.run.event;

import java.time.OffsetDateTime;

/**
 * Body of {@code GET /api/events}. A DTO so the JPA entities (and their lazy relations)
 * never leak out of the API. The location is flattened to the two fields the UI needs.
 */
public record EventResponse(
		Long id,
		String name,
		OffsetDateTime startDateTime,
		String locationName,
		String city) {

	static EventResponse from(Event event) {
		return new EventResponse(
				event.getId(),
				event.getName(),
				event.getStartDateTime(),
				event.getLocation().getName(),
				event.getLocation().getCity());
	}

}