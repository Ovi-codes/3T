package ro.threet.run.event;

import java.time.OffsetDateTime;

/**
 * Announced once a run's <em>start</em> has been moved <em>and the change has committed</em> — a
 * rename on its own doesn't raise it (issue #58). Carries the id and both the old and the new start
 * so the listener can tell registrants what changed without re-reading the previous value (the event
 * row now holds only the new one).
 *
 * <p>Same shape and reasoning as {@link EventCancelled}: a published event, not a direct call, so the
 * registrant mailer runs after the commit and a mail server that is down can't roll the reschedule
 * back — the edit is the authoritative act, the emails best-effort (ADR-0001).
 */
public record EventRescheduled(Long eventId, OffsetDateTime previousStart, OffsetDateTime newStart) {
}
