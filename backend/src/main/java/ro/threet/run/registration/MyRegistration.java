package ro.threet.run.registration;

import java.time.OffsetDateTime;

/**
 * One of the current user's registrations, flattened for the dashboard — the run's name, when and
 * where it is, whether it has been called off, plus the registration id. A DTO so the JPA entities
 * never leak out of the API.
 *
 * <p>{@code finishTimeSeconds} is the runner's own recorded time for a past run, in whole seconds,
 * or null when they haven't entered one (#43).
 *
 * <p>{@code cancelled} is why a cancelled run keeps showing here after it has dropped off the public
 * homepage: the people who signed up are told, and go on seeing it (badged) rather than watching it
 * vanish without explanation (ADR-0001).
 */
public record MyRegistration(
		Long registrationId,
		Long eventId,
		String eventName,
		OffsetDateTime startDateTime,
		String locationName,
		String city,
		boolean cancelled,
		Integer finishTimeSeconds) {

	static MyRegistration from(Registration registration) {
		var event = registration.getEvent();
		var location = event.getLocation();
		return new MyRegistration(
				registration.getId(),
				event.getId(),
				event.getName(),
				event.getStartDateTime(),
				location.getName(),
				location.getCity(),
				event.isCancelled(),
				registration.getFinishTimeSeconds());
	}

}
