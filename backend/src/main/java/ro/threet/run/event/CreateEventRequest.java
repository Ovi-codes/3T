package ro.threet.run.event;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The admin create-event payload (issue #57). Bean Validation runs at the controller boundary
 * (@Valid), so a missing/too-long name or a missing start is a 400 with field errors before any work
 * happens.
 *
 * <p>{@code startDateTime} is a wall-clock {@link LocalDateTime} — the date + time the admin entered.
 * The service interprets it in Europe/Bucharest and persists the resulting instant; keeping the zone
 * logic server-side means the client never has to reason about offsets. Whether that instant is in
 * the future is a business rule checked in the service (against the app clock, in the same zone), not
 * a bean-validation annotation.
 */
public record CreateEventRequest(

		@NotBlank(message = "Enter a name for the run.")
		@Size(max = 160, message = "Name is too long.")
		String name,

		@NotNull(message = "Choose a start date and time.")
		LocalDateTime startDateTime) {
}
