package ro.threet.run.registration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ro.threet.run.event.EventCancelled;

/**
 * Tells everyone registered for a run that it has been called off (issue #58).
 *
 * <p>Runs <strong>after</strong> the cancelling transaction commits, which is the whole point: the
 * cancellation is the authoritative act and the emails are best-effort, so a mail server that is
 * down can't roll it back (ADR-0001). The dedupe-send-log mechanics — one message per distinct
 * registrant, failures logged and stepped over — live in {@link RegistrantMailout}, shared with the
 * reschedule notice; this class only builds the cancellation wording.
 */
@Component
class CancellationNotifier {

	private final RegistrantMailout mailout;

	CancellationNotifier(RegistrantMailout mailout) {
		this.mailout = mailout;
	}

	@TransactionalEventListener
	public void onEventCancelled(EventCancelled cancelled) {
		mailout.notifyRegistrants(cancelled.eventId(), "cancellation",
				registration -> CancellationEmail.forRegistration(registration, cancelled.reason()));
	}

}
