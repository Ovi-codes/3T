package ro.threet.run.event;

/**
 * Announced once an event has been cancelled <em>and the change has committed</em>.
 *
 * <p>Carries the id and the {@code reason} wording the admin gave — plain text, resolved on the
 * frontend from a standard reason or their own words: whoever reacts reads the rest for itself.
 * Today the one listener is the registrant mailer in the registration package, which is exactly why
 * this is a published event rather than a direct call — the listener runs after the commit, so a
 * mail server that is down cannot roll the cancellation back, and the cancel stays the authoritative
 * act with the emails best-effort (ADR-0001).
 */
public record EventCancelled(Long eventId, String reason) {
}
