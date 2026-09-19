package ro.threet.run.registration;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

	/**
	 * Whether this email is already registered for this event. Backs the friendly duplicate
	 * check; the {@code unique(event_id, email)} constraint is the race-proof backstop.
	 */
	boolean existsByEventIdAndEmailIgnoreCase(Long eventId, String email);

	/** How many people are registered for one event — the admin's count, and the delete guard. */
	long countByEventId(Long eventId);

	/**
	 * The registration count for each of several events in one query, so the admin schedule doesn't
	 * fan out into a count per row. Events with no registrations simply don't come back.
	 */
	@Query("""
			select new ro.threet.run.registration.EventRegistrationCount(r.event.id, count(r))
			from Registration r
			where r.event.id in :eventIds
			group by r.event.id""")
	List<EventRegistrationCount> countByEventIdIn(Collection<Long> eventIds);

	/**
	 * Everyone registered for one event, oldest first, with the event and its location eagerly
	 * joined — the cancellation mailer reads the run's name, date and place off each row and runs
	 * outside the cancelling transaction, so the lazy relations must already be resolved.
	 */
	@Query("""
			select r from Registration r
			join fetch r.event e
			join fetch e.location
			where e.id = :eventId
			order by r.id asc""")
	List<Registration> findByEventIdWithEvent(Long eventId);

	/**
	 * One account's registrations, newest run first, with the event and its location eagerly
	 * joined — the dashboard reads event name, date, and place off each row, so fetching them in
	 * one query avoids an N+1 walk over the lazy relations. The service splits the list into the
	 * upcoming/past buckets against its clock.
	 */
	@Query("""
			select r from Registration r
			join fetch r.event e
			join fetch e.location
			where r.userId = :userId
			order by e.startDateTime desc""")
	List<Registration> findByUserIdWithEvent(Long userId);

	/**
	 * Delete every registration made by an account — the registration side of GDPR erasure
	 * (charter §7). Anonymous rows keep {@code user_id} null and are left untouched. Returns how
	 * many were removed.
	 */
	long deleteByUserId(Long userId);

}