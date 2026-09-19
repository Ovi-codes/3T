package ro.threet.run.registration;

import java.time.OffsetDateTime;

import ro.threet.run.event.Event;

/**
 * Builds the email that tells a registrant their run has moved — naming the event, the old time and
 * the new one, and its location. See {@link RegistrantEmail} for the shared shape (the transport
 * split, the local Bucharest time, the venue line).
 *
 * <p>The old and new starts come from the {@code EventRescheduled} event (the row now holds only the
 * new one).
 */
final class RescheduleEmail extends RegistrantEmail {

	private RescheduleEmail(String subject, String body) {
		super(subject, body);
	}

	static RescheduleEmail forRegistration(Registration registration, OffsetDateTime previousStart,
			OffsetDateTime newStart) {
		Event event = registration.getEvent();
		String subject = "Rescheduled: " + event.getName();
		String body = """
				Hi %s,

				Heads up — %s has been rescheduled. Please note the new details.

				Was: %s
				Now: %s
				Where: %s

				You're still registered; there's nothing to do. See you there!

				The 3T Run team""".formatted(registration.getName(), event.getName(),
				when(previousStart), when(newStart), where(event));

		return new RescheduleEmail(subject, body);
	}

}
