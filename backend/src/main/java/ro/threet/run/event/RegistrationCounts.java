package ro.threet.run.event;

import java.util.Collection;
import java.util.Map;

/**
 * How many people are registered for an event — the only thing the events slice needs to know about
 * registrations. A seam (charter §3) rather than a direct call into the registration package: events
 * would otherwise depend on registrations and registrations already depend on events, and a count is
 * all that's wanted. The implementation lives next to the registration data it reads.
 *
 * <p>Deliberately counts only. Who the registrants are never crosses this boundary: the admin UI
 * shows a number, and the one place registrant addresses are read is the cancellation mailer, inside
 * the registration package.
 */
public interface RegistrationCounts {

	/** How many registrations this event has. */
	long forEvent(Long eventId);

	/**
	 * How many registrations each of these events has, in one query. Events nobody has registered
	 * for are absent from the map — callers read a missing entry as zero.
	 */
	Map<Long, Long> forEvents(Collection<Long> eventIds);

}
