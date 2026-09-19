package ro.threet.run.registration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ro.threet.run.email.EmailSender;
import ro.threet.run.event.EventRescheduled;

/**
 * Tells everyone registered for a run that its time has moved (issue #58).
 *
 * <p>The twin of {@link CancellationNotifier}, and for the same reasons: it runs <strong>after</strong>
 * the editing transaction commits (a mail server that is down can't roll the reschedule back — the
 * edit is authoritative, the emails best-effort, ADR-0001), each message is sent on its own with
 * failures logged and stepped over, and recipients are deduped by lower-cased email so one person
 * with rows under the same address is emailed once.
 */
@Component
class RescheduleNotifier {

	private static final Logger log = LoggerFactory.getLogger(RescheduleNotifier.class);

	private final RegistrationRepository registrations;
	private final EmailSender emailSender;

	RescheduleNotifier(RegistrationRepository registrations, EmailSender emailSender) {
		this.registrations = registrations;
		this.emailSender = emailSender;
	}

	@TransactionalEventListener
	public void onEventRescheduled(EventRescheduled rescheduled) {
		Map<String, Registration> recipients = new LinkedHashMap<>();
		for (Registration registration : registrations.findByEventIdWithEvent(rescheduled.eventId())) {
			recipients.putIfAbsent(registration.getEmail().toLowerCase(Locale.ROOT), registration);
		}

		int sent = 0;
		for (Registration registration : recipients.values()) {
			RescheduleEmail email = RescheduleEmail.forRegistration(registration,
					rescheduled.previousStart(), rescheduled.newStart());
			try {
				emailSender.send(registration.getEmail(), email.subject(), email.body());
				sent++;
			}
			catch (RuntimeException failure) {
				// Best-effort by design: the run stays rescheduled whatever the mail server does.
				log.warn("Could not email the reschedule of event {} to registration {}",
						rescheduled.eventId(), registration.getId(), failure);
			}
		}
		log.info("Event {} rescheduled: notified {} of {} registrant(s)", rescheduled.eventId(), sent,
				recipients.size());
	}

}
