package ro.threet.run.registration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ro.threet.run.event.EventRescheduled;

/**
 * Tells everyone registered for a run that its time has moved (issue #58).
 *
 * <p>The twin of {@link CancellationNotifier}, and for the same reasons: it runs <strong>after</strong>
 * the editing transaction commits (a mail server that is down can't roll the reschedule back — the
 * edit is authoritative, the emails best-effort, ADR-0001). The dedupe-send-log mechanics are shared
 * in {@link RegistrantMailout}; this class only builds the reschedule wording.
 */
@Component
class RescheduleNotifier {

	private final RegistrantMailout mailout;

	RescheduleNotifier(RegistrantMailout mailout) {
		this.mailout = mailout;
	}

	@TransactionalEventListener
	public void onEventRescheduled(EventRescheduled rescheduled) {
		mailout.notifyRegistrants(rescheduled.eventId(), "reschedule",
				registration -> RescheduleEmail.forRegistration(registration,
						rescheduled.previousStart(), rescheduled.newStart()));
	}

}
