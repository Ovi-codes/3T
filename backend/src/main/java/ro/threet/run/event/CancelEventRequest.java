package ro.threet.run.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The admin cancel-event payload (issue #58) — why the run is being called off, as plain text.
 *
 * <p>The backend does not enumerate reasons. The admin UI offers a picker of standard reasons plus a
 * free-text "other", but whichever the admin lands on arrives here as the finished wording; the
 * service just relays this text into the cancellation email.
 *
 * <p>A {@code reason} is mandatory and length-capped: blank (or whitespace-only) is a 400 on
 * {@code reason}, as is omitting it.
 */
public record CancelEventRequest(

		@NotBlank(message = "Enter a reason for cancelling.")
		@Size(max = 200, message = "Reason is too long.")
		String reason) {
}
