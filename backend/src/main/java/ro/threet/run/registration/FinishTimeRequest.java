package ro.threet.run.registration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The finish-time payload (#43): the runner's own time for a past run, in whole seconds. Seconds
 * keep the wire unambiguous and leaderboard-ready; the UI parses and formats {@code mm:ss}.
 *
 * <p>Bean Validation runs at the controller boundary (@Valid): a missing time, or one outside the
 * sensible 5k window of 10:00 to 1:00:00, is a 400 with a field error before any work happens.
 * Whether the run has taken place, and whose registration it is, are the service's call.
 */
public record FinishTimeRequest(

		@NotNull(message = "Enter your finish time.")
		@Min(value = 600, message = "That's faster than 10:00 — check your time.")
		@Max(value = 3600, message = "That's slower than 1:00:00 — check your time.")
		Integer finishTimeSeconds) {
}
