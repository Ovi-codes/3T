package ro.threet.run.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The sign-up payload. Validated at the controller boundary (@Valid), so a missing name, a bad
 * email, or a too-short password is a 400 with field errors before any account work happens. The
 * 72-character ceiling on the password is BCrypt's own limit — it silently ignores bytes past 72,
 * so we reject rather than quietly truncate. The name mirrors the registration form's server-side
 * rule (present, not too long); the stricter shape checks (min length, not only digits) live in the
 * UI, as they do for registration.
 */
public record SignupRequest(

		@NotBlank(message = "Enter your name.")
		@Size(max = 120, message = "Name is too long.")
		String name,

		@NotBlank(message = "Enter your email.")
		@Email(message = "Enter a valid email address.")
		@Size(max = 254, message = "Email is too long.")
		String email,

		@NotBlank(message = "Choose a password.")
		@Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
		String password) {
}
