package ro.threet.run.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The sign-up payload. Validated at the controller boundary (@Valid), so a bad email or a too-short
 * password is a 400 with field errors before any account work happens. The 72-character ceiling is
 * BCrypt's own limit — it silently ignores bytes past 72, so we reject rather than quietly truncate.
 */
public record SignupRequest(

		@NotBlank(message = "Enter your email.")
		@Email(message = "Enter a valid email address.")
		@Size(max = 254, message = "Email is too long.")
		String email,

		@NotBlank(message = "Choose a password.")
		@Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
		String password) {
}
