package ro.threet.run.registration;

/**
 * A built email — its subject and plain-text body. The one thing that differs between the
 * registrant mail-outs (cancellation, reschedule), so {@link RegistrantMailout} takes a builder that
 * produces this and each email class implements it while keeping its own wording.
 */
interface MailMessage {

	String subject();

	String body();

}
