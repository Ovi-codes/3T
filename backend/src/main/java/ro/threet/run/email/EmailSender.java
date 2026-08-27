package ro.threet.run.email;

/**
 * Sends a transactional email. The one seam the rest of the app depends on, so the transport
 * (SMTP in dev/prod, a fake in unit tests) stays swappable — charter §3.
 */
public interface EmailSender {

	/**
	 * Send a plain-text message. Throws if the message cannot be handed to the transport, so a
	 * caller that must not lose the mail can let the failure roll its work back.
	 */
	void send(String to, String subject, String body);

}