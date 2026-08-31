package ro.threet.run.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ro.threet.run.auth.EmailAlreadyUsedException;
import ro.threet.run.registration.RegistrationException;

/**
 * Turns validation and business failures into one predictable JSON shape:
 * {@code {"errors": {"<field>": "<message>"}}}. Bean Validation (a missing name, a malformed
 * email) and business rules (unknown/past event, duplicate registration) come back the same way,
 * so the form can surface either against the right field.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	public record ApiErrors(Map<String, String> errors) {
	}

	/** Payload failed {@code @Valid} — one message per field, first wins. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrors> onValidation(MethodArgumentNotValidException exception) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (var fieldError : exception.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return ResponseEntity.badRequest().body(new ApiErrors(errors));
	}

	/** A business rule rejected the request — the exception carries its field and status. */
	@ExceptionHandler(RegistrationException.class)
	public ResponseEntity<ApiErrors> onRegistration(RegistrationException exception) {
		HttpStatus status = exception.status();
		Map<String, String> errors = Map.of(exception.field(), exception.getMessage());
		return ResponseEntity.status(status).body(new ApiErrors(errors));
	}

	/** Sign-up hit a taken email — a 409 tied to the email field. */
	@ExceptionHandler(EmailAlreadyUsedException.class)
	public ResponseEntity<ApiErrors> onEmailAlreadyUsed(EmailAlreadyUsedException exception) {
		Map<String, String> errors = Map.of("email", exception.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrors(errors));
	}

	/**
	 * Login failed. One generic, form-level message under {@code credentials} — never a field-level
	 * hint about whether the email or the password was the wrong one.
	 */
	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ApiErrors> onBadCredentials(BadCredentialsException exception) {
		Map<String, String> errors = Map.of("credentials", exception.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiErrors(errors));
	}

}