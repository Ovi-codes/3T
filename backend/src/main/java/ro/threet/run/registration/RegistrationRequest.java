package ro.threet.run.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The registration form payload. Bean Validation runs at the controller boundary (@Valid), so a
 * missing name or a malformed email is a 400 with field errors before any work happens — nothing
 * is persisted and no email is sent.
 */
public record RegistrationRequest(

		@NotNull(message = "Choose an event to register for.")
		Long eventId,

		@NotBlank(message = "Enter your name.")
		@Size(max = 120, message = "Name is too long.")
		String name,

		@NotBlank(message = "Enter your email.")
		@Email(message = "Enter a valid email address.")
		@Size(max = 254, message = "Email is too long.")
		String email) {
}