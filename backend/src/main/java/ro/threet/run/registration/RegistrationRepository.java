package ro.threet.run.registration;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

	/**
	 * Whether this email is already registered for this event. Backs the friendly duplicate
	 * check; the {@code unique(event_id, email)} constraint is the race-proof backstop.
	 */
	boolean existsByEventIdAndEmailIgnoreCase(Long eventId, String email);

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