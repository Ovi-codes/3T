package ro.threet.run.registration;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import ro.threet.run.event.Event;

/**
 * The shared shape of the plain-text emails sent to a registrant — confirmation, cancellation,
 * reschedule. Each is a built {@code subject}/{@code body} pair, kept apart from the transport
 * ({@link ro.threet.run.email.EmailSender}) so wording changes never touch the SMTP code. The stored
 * instant is UTC; every email shows the run in local Bucharest time — when it happens for the reader
 * — at the same venue line. Only the wording differs, so a subclass supplies just its
 * {@code forRegistration} factory and template, using {@link #when} and {@link #where} for the parts
 * that read the same everywhere.
 */
abstract class RegistrantEmail {

	private static final ZoneId EVENT_ZONE = ZoneId.of("Europe/Bucharest");
	private static final DateTimeFormatter WHEN =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

	private final String subject;
	private final String body;

	RegistrantEmail(String subject, String body) {
		this.subject = subject;
		this.body = body;
	}

	/** An instant as local Bucharest wall-clock time — when the run happens for the reader. */
	static String when(OffsetDateTime instant) {
		return instant.atZoneSameInstant(EVENT_ZONE).format(WHEN);
	}

	/** The venue line shared by every email: name, city. */
	static String where(Event event) {
		return event.getLocation().getName() + ", " + event.getLocation().getCity();
	}

	String subject() {
		return subject;
	}

	String body() {
		return body;
	}

}
