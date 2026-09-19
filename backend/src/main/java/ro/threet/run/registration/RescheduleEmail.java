package ro.threet.run.registration;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import ro.threet.run.event.Event;

/**
 * Builds the email that tells a registrant their run has moved — subject and plain-text body, naming
 * the event, the old time and the new one, and its location. The sibling of {@link ConfirmationEmail}
 * and {@link CancellationEmail}: same split, so wording changes never touch the SMTP code.
 *
 * <p>The old and new starts come from the {@code EventRescheduled} event (the row now holds only the
 * new one); both are shown in local Bucharest time, which is when the run happens for the reader.
 */
final class RescheduleEmail implements MailMessage {

	private static final ZoneId EVENT_ZONE = ZoneId.of("Europe/Bucharest");
	private static final DateTimeFormatter WHEN =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

	private final String subject;
	private final String body;

	private RescheduleEmail(String subject, String body) {
		this.subject = subject;
		this.body = body;
	}

	static RescheduleEmail forRegistration(Registration registration, OffsetDateTime previousStart,
			OffsetDateTime newStart) {
		Event event = registration.getEvent();
		String was = previousStart.atZoneSameInstant(EVENT_ZONE).format(WHEN);
		String now = newStart.atZoneSameInstant(EVENT_ZONE).format(WHEN);
		String where = event.getLocation().getName() + ", " + event.getLocation().getCity();

		String subject = "Rescheduled: " + event.getName();
		String body = """
				Hi %s,

				Heads up — %s has been rescheduled. Please note the new details.

				Was: %s
				Now: %s
				Where: %s

				You're still registered; there's nothing to do. See you there!

				The 3T Run team""".formatted(registration.getName(), event.getName(), was, now, where);

		return new RescheduleEmail(subject, body);
	}

	@Override
	public String subject() {
		return subject;
	}

	@Override
	public String body() {
		return body;
	}

}
