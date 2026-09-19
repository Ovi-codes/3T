package ro.threet.run.registration;

import ro.threet.run.event.Event;

/**
 * Builds the email that tells a registrant their run is off — naming the event, why it was called
 * off, the date it would have been, and its location. See {@link RegistrantEmail} for the shared
 * shape (the transport split, the local Bucharest time, the venue line).
 */
final class CancellationEmail extends RegistrantEmail {

	private CancellationEmail(String subject, String body) {
		super(subject, body);
	}

	static CancellationEmail forRegistration(Registration registration, String reason) {
		Event event = registration.getEvent();
		String subject = "Cancelled: " + event.getName();
		String body = """
				Hi %s,

				We're sorry — %s has been cancelled due to %s.

				Initial date: %s
				Where: %s

				You don't need to do anything. Keep an eye on the site for the next run.

				The 3T Run team""".formatted(registration.getName(), event.getName(), reason,
				when(event.getStartDateTime()), where(event));

		return new CancellationEmail(subject, body);
	}

}
