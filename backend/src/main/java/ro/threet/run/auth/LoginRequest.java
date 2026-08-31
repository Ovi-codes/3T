package ro.threet.run.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * The login payload. Both fields are merely required here — the strength rules belong to sign-up;
 * a wrong or malformed login is answered with one generic 401, never a field-level hint about which
 * part was wrong.
 */
public record LoginRequest(

		@NotBlank(message = "Enter your email.")
		String email,

		@NotBlank(message = "Enter your password.")
		String password) {
}
