package ro.threet.run.event;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The admin edit-event payload (issue #58) — the whole of what an admin may change about a run: its
 * name and when it starts. Where it is held isn't editable while there is one location (the
 * {@code Location} seam, charter §3), so no location field.
 *
 * <p>Bean Validation runs at the controller boundary (@Valid), so a missing/too-long name or a
 * missing start is a 400 with field errors before any work happens. Like the create payload,
 * {@code startDateTime} is a wall-clock {@link LocalDateTime}: the service interprets it in
 * Europe/Bucharest, and whether the result is still in the future is a business rule checked there.
 * Whether the event may be edited at all (a cancelled or already-run event is read-only) is also the
 * service's call.
 */
public record UpdateEventRequest(

		@NotBlank(message = "Enter a name for the run.")
		@Size(max = 160, message = "Name is too long.")
		String name,

		@NotNull(message = "Choose a start date and time.")
		LocalDateTime startDateTime) {
}
