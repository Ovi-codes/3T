package ro.threet.run.registration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import ro.threet.run.event.Event;

/**
 * Builds the transactional confirmation email for a registration — subject and plain-text body,
 * naming the event, its date and its location. Kept separate from the transport ({@link
 * ro.threet.run.email.EmailSender}) so wording changes never touch the SMTP code.
 *
 * The stored instant is UTC; the email shows the local Bucharest time, which is when the run
 * actually happens for the reader.
 */
final class ConfirmationEmail {

	private static final ZoneId EVENT_ZONE = ZoneId.of("Europe/Bucharest");
	private static final DateTimeFormatter WHEN =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

	private final String subject;
	private final String body;

	private ConfirmationEmail(String subject, String body) {
		this.subject = subject;
		this.body = body;
	}

	static ConfirmationEmail forRegistration(Registration registration) {
		Event event = registration.getEvent();
		String when = event.getStartDateTime().atZoneSameInstant(EVENT_ZONE).format(WHEN);
		String where = event.getLocation().getName() + ", " + event.getLocation().getCity();

		String subject = "You're registered for " + event.getName();
		String body = """
				Hi %s,

				You're registered for %s.

				When: %s
				Where: %s

				Just turn up and have fun. See you there!

				The 3T Run team""".formatted(registration.getName(), event.getName(), when, where);

		return new ConfirmationEmail(subject, body);
	}

	String subject() {
		return subject;
	}

	String body() {
		return body;
	}

}