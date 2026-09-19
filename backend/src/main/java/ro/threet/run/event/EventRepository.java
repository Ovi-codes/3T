package ro.threet.run.event;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

	/**
	 * What the public sees: events starting at or after {@code from} that are still on, soonest
	 * first. Cancelled runs are excluded here — they drop off the homepage the moment they're called
	 * off (ADR-0001). The DB does the filtering and ordering; the cutoff comes from the service's
	 * clock.
	 */
	List<Event> findByStatusAndStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(
			EventStatus status, OffsetDateTime from);

	/**
	 * What an admin sees: every event starting at or after {@code from}, cancelled ones included, so
	 * a called-off run stays on the admin's schedule (read-only, badged) instead of vanishing.
	 */
	List<Event> findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(OffsetDateTime from);

}
