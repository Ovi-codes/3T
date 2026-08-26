package ro.threet.run.event;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

	/**
	 * Upcoming events — starting at or after {@code from} — soonest first. The DB does the
	 * filtering and ordering; the cutoff comes from the service's clock.
	 */
	List<Event> findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(OffsetDateTime from);

}