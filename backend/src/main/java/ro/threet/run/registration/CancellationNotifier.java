package ro.threet.run.registration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ro.threet.run.email.EmailSender;
import ro.threet.run.event.EventCancelled;

/**
 * Tells everyone registered for a run that it has been called off (issue #58).
 *
 * <p>Runs <strong>after</strong> the cancelling transaction commits, which is the whole point: the
 * cancellation is the authoritative act and the emails are best-effort, so a mail server that is
 * down can't roll it back (ADR-0001). Each message is sent on its own and a failure is logged and
 * stepped over, so one bad address doesn't cost the rest of the list their notice.
 *
 * <p>Deduped by email: the same person can hold rows under one address for a run at most once
 * (the {@code unique(event_id, email)} constraint), but the dedupe is kept explicit so the guarantee
 * lives here rather than in a constraint this class doesn't own. Case-insensitive, since that is how
 * the duplicate check treats addresses.
 */
@Component
class CancellationNotifier {

	private static final Logger log = LoggerFactory.getLogger(CancellationNotifier.class);

	private final RegistrationRepository registrations;
	private final EmailSender emailSender;

	CancellationNotifier(RegistrationRepository registrations, EmailSender emailSender) {
		this.registrations = registrations;
		this.emailSender = emailSender;
	}

	@TransactionalEventListener
	public void onEventCancelled(EventCancelled cancelled) {
		Map<String, Registration> recipients = new LinkedHashMap<>();
		for (Registration registration : registrations.findByEventIdWithEvent(cancelled.eventId())) {
			recipients.putIfAbsent(registration.getEmail().toLowerCase(Locale.ROOT), registration);
		}

		int sent = 0;
		for (Registration registration : recipients.values()) {
			CancellationEmail email = CancellationEmail.forRegistration(registration);
			try {
				emailSender.send(registration.getEmail(), email.subject(), email.body());
				sent++;
			}
			catch (RuntimeException failure) {
				// Best-effort by design: the run stays cancelled whatever the mail server does.
				log.warn("Could not email the cancellation of event {} to registration {}",
						cancelled.eventId(), registration.getId(), failure);
			}
		}
		log.info("Event {} cancelled: notified {} of {} registrant(s)", cancelled.eventId(), sent,
				recipients.size());
	}

}
