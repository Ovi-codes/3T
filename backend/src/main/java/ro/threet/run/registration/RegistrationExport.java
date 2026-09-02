package ro.threet.run.registration;

import java.time.OffsetDateTime;

/**
 * One of a user's registrations, flattened for a GDPR data export (charter §7): the run it was for,
 * plus the personal data held on the row — the name and email given, and when it was made. Unlike
 * {@link MyRegistration} (the dashboard view), this carries the participant's own name and email,
 * because an export is precisely the personal data we hold about them.
 */
public record RegistrationExport(
		Long registrationId,
		String eventName,
		OffsetDateTime eventStartDateTime,
		String locationName,
		String city,
		String participantName,
		String email,
		OffsetDateTime registeredAt) {

	static RegistrationExport from(Registration registration) {
		var event = registration.getEvent();
		var location = event.getLocation();
		return new RegistrationExport(
				registration.getId(),
				event.getName(),
				event.getStartDateTime(),
				location.getName(),
				location.getCity(),
				registration.getName(),
				registration.getEmail(),
				registration.getCreatedAt());
	}

}
