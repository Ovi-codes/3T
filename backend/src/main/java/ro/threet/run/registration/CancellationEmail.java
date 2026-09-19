package ro.threet.run.registration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import ro.threet.run.event.Event;

/**
 * Builds the email that tells a registrant their run is off — subject and plain-text body, naming
 * the event, why it was called off, the date it would have been, and its location. The sibling of
 * {@link ConfirmationEmail}: same split, so wording changes never touch the SMTP code.
 *
 * The stored instant is UTC; the email shows the local Bucharest time, which is when the run would
 * have happened for the reader.
 */
final class CancellationEmail {

	private static final ZoneId EVENT_ZONE = ZoneId.of("Europe/Bucharest");
	private static final DateTimeFormatter WHEN =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

	private final String subject;
	private final String body;

	private CancellationEmail(String subject, String body) {
		this.subject = subject;
		this.body = body;
	}

	static CancellationEmail forRegistration(Registration registration, String reason) {
		Event event = registration.getEvent();
		String when = event.getStartDateTime().atZoneSameInstant(EVENT_ZONE).format(WHEN);
		String where = event.getLocation().getName() + ", " + event.getLocation().getCity();

		String subject = "Cancelled: " + event.getName();
		String body = """
				Hi %s,

				We're sorry — %s has been cancelled due to %s.

				Initial date: %s
				Where: %s

				You don't need to do anything. Keep an eye on the site for the next run.

				The 3T Run team""".formatted(registration.getName(), event.getName(), reason, when, where);

		return new CancellationEmail(subject, body);
	}

	String subject() {
		return subject;
	}

	String body() {
		return body;
	}

}
