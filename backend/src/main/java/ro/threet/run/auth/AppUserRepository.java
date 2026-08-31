package ro.threet.run.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

	/** Emails are stored lower-cased, so an exact match is a case-insensitive lookup. */
	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

}
