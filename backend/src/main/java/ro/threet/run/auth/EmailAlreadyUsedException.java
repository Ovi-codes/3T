package ro.threet.run.auth;

/**
 * Sign-up was attempted with an email that already has an account. Rendered as a 409 with the
 * message tied to the {@code email} field, the same {@code {"errors": {...}}} shape as every other
 * form failure, so the sign-up form can show it inline.
 */
public class EmailAlreadyUsedException extends RuntimeException {

	public EmailAlreadyUsedException(String message) {
		super(message);
	}

}
