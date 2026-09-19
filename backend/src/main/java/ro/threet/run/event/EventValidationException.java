package ro.threet.run.event;

/**
 * An event that fails a rule the payload alone can't express — currently a start that isn't in the
 * future (checked against the app clock, in Europe/Bucharest). Carries the field it concerns so
 * {@link ro.threet.run.web.ApiExceptionHandler} renders it the same {@code {errors:{field:msg}}}
 * shape as a bean-validation error and the admin form shows it inline. Always a 400.
 */
public class EventValidationException extends RuntimeException {

	private final String field;

	public EventValidationException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}

}
