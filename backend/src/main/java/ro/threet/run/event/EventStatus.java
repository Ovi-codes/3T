package ro.threet.run.event;

/**
 * Where an event stands. {@code SCHEDULED} is every run that is still on; {@code CANCELLED} is the
 * soft, <strong>terminal</strong> state an admin puts a run into when it can't go ahead but people
 * have already registered — there is deliberately no un-cancel, and no third state.
 *
 * <p>Cancelling is not deleting: see
 * <a href="../../../../../../../docs/adr/0001-cancel-vs-hard-delete-events.md">ADR-0001</a>. A
 * cancelled run drops off the public {@code GET /api/events}, stays badged in its registrants'
 * dashboards until its date passes, and keeps every registration row — removing an event must never
 * silently destroy someone else's personal data.
 */
public enum EventStatus {

	SCHEDULED,
	CANCELLED

}
