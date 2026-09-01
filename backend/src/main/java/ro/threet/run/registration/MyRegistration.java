package ro.threet.run.registration;

import java.time.OffsetDateTime;

/**
 * One of the current user's registrations, flattened for the dashboard — the run's name, when and
 * where it is, plus the registration id. A DTO so the JPA entities never leak out of the API.
 */
public record MyRegistration(
		Long registrationId,
		Long eventId,
		String eventName,
		OffsetDateTime startDateTime,
		String locationName,
		String city) {

	static MyRegistration from(Registration registration) {
		var event = registration.getEvent();
		var location = event.getLocation();
		return new MyRegistration(
				registration.getId(),
				event.getId(),
				event.getName(),
				event.getStartDateTime(),
				location.getName(),
				location.getCity());
	}

}
