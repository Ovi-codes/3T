package ro.threet.run.registration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

	/**
	 * Whether this email is already registered for this event. Backs the friendly duplicate
	 * check; the {@code unique(event_id, email)} constraint is the race-proof backstop.
	 */
	boolean existsByEventIdAndEmailIgnoreCase(Long eventId, String email);

}