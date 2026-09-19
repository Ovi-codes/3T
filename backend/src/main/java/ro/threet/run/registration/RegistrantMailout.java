package ro.threet.run.registration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ro.threet.run.email.EmailSender;

/**
 * The shared best-effort mail-out behind {@link CancellationNotifier} and {@link RescheduleNotifier}
 * (issue #58). Both tell every registrant of a run about a change that has <strong>already
 * committed</strong>, and both do it the same way, which is what lives here:
 *
 * <ul>
 * <li>dedupe the list by lower-cased email so one person is told at most once — the
 * {@code unique(event_id, email)} constraint already guarantees this, but the dedupe is kept
 * explicit so the guarantee lives with the send rather than in a constraint this class doesn't own;
 * <li>send each message on its own so one bad address doesn't cost the rest of the list their notice;
 * <li>log and step over a failure — the change is authoritative and the emails best-effort, so a
 * mail server that is down can't undo it (ADR-0001);
 * <li>log a one-line summary of how many were reached.
 * </ul>
 *
 * <p>Only the message differs, so each caller passes a {@code build} that turns a registration into
 * its own subject and body, and a short {@code change} label (e.g. {@code "cancellation"},
 * {@code "reschedule"}) that names the mail-out in the log lines.
 */
@Component
class RegistrantMailout {

	private static final Logger log = LoggerFactory.getLogger(RegistrantMailout.class);

	private final RegistrationRepository registrations;
	private final EmailSender emailSender;

	RegistrantMailout(RegistrationRepository registrations, EmailSender emailSender) {
		this.registrations = registrations;
		this.emailSender = emailSender;
	}

	/**
	 * Emails every distinct registrant of {@code eventId} the message {@code build} makes for them.
	 * Never throws: a send that fails is logged and skipped, so the committed change stands whatever
	 * the mail server does.
	 */
	void notifyRegistrants(long eventId, String change, Function<Registration, MailMessage> build) {
		Map<String, Registration> recipients = new LinkedHashMap<>();
		for (Registration registration : registrations.findByEventIdWithEvent(eventId)) {
			recipients.putIfAbsent(registration.getEmail().toLowerCase(Locale.ROOT), registration);
		}

		int sent = 0;
		for (Registration registration : recipients.values()) {
			MailMessage message = build.apply(registration);
			try {
				emailSender.send(registration.getEmail(), message.subject(), message.body());
				sent++;
			}
			catch (RuntimeException failure) {
				// Best-effort by design: the change stands whatever the mail server does.
				log.warn("Could not email the {} of event {} to registration {}", change, eventId,
						registration.getId(), failure);
			}
		}
		log.info("Event {} {}: notified {} of {} registrant(s)", eventId, change, sent, recipients.size());
	}

}
