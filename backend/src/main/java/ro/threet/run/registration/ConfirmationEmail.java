package ro.threet.run.registration;

import ro.threet.run.event.Event;

/**
 * Builds the transactional confirmation email for a registration — naming the event, its date and
 * its location. See {@link RegistrantEmail} for the shared shape (the transport split, the local
 * Bucharest time, the venue line).
 */
final class ConfirmationEmail extends RegistrantEmail {

	private ConfirmationEmail(String subject, String body) {
		super(subject, body);
	}

	static ConfirmationEmail forRegistration(Registration registration) {
		Event event = registration.getEvent();
		String subject = "You're registered for " + event.getName();
		String body = """
				Hi %s,

				You're registered for %s.

				When: %s
				Where: %s

				Just turn up and have fun. See you there!

				The 3T Run team""".formatted(registration.getName(), event.getName(),
				when(event.getStartDateTime()), where(event));

		return new ConfirmationEmail(subject, body);
	}

}
