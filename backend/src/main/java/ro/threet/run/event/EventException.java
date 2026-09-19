package ro.threet.run.event;

import org.springframework.http.HttpStatus;

/**
 * An event operation the payload alone can't rule out: a start that isn't in the future (400), an id
 * that matches nothing (404), or a rule that the event's own state forbids (409 — editing or
 * cancelling a run that is already cancelled or already over, deleting one people have registered
 * for).
 *
 * <p>Carries the status and the field it concerns so {@link ro.threet.run.web.ApiExceptionHandler}
 * renders it in the same {@code {errors:{field:msg}}} shape as a bean-validation error, and the
 * admin UI can show it either inline against a field or as a whole-form message.
 */
public class EventException extends RuntimeException {

	/** The key a whole-event problem is reported under, when no single input field is at fault. */
	public static final String EVENT_FIELD = "event";

	private final HttpStatus status;
	private final String field;

	/** A rejected input value — always a 400 against the named field. */
	public EventException(String field, String message) {
		this(HttpStatus.BAD_REQUEST, field, message);
	}

	public EventException(HttpStatus status, String field, String message) {
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
