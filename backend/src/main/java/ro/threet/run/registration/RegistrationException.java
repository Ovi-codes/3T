package ro.threet.run.registration;

import org.springframework.http.HttpStatus;

/**
 * A registration that fails a rule the payload alone can't express — an unknown or past event, or
 * an email already registered for the run. Carries the field it concerns and the status to return,
 * so {@link ro.threet.run.web.ApiExceptionHandler} can render it the same shape as a validation
 * error and the form can show it inline.
 */
public class RegistrationException extends RuntimeException {

	private final HttpStatus status;
	private final String field;

	public RegistrationException(HttpStatus status, String field, String message) {
		super(message);
		this.status = status;
		this.field = field;
	}

	public HttpStatus status() {
		return status;
	}

	public String field() {
		return field;
	}

}