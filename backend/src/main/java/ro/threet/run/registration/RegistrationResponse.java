package ro.threet.run.registration;

import java.time.OffsetDateTime;

/**
 * What a successful registration returns — enough for the confirmation view to name the run
 * back to the visitor without a second request.
 */
public record RegistrationResponse(Long id, Long eventId, String eventName,
		OffsetDateTime startDateTime, String locationName, String city,
		String name, String email) {

	static RegistrationResponse from(Registration registration) {
		var event = registration.getEvent();
		var location = event.getLocation();
		return new RegistrationResponse(registration.getId(), event.getId(), event.getName(),
				event.getStartDateTime(), location.getName(), location.getCity(),
				registration.getName(), registration.getEmail());
	}

}