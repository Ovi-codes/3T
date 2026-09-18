package ro.threet.run.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<Role, Long> {

	/** Look up a seeded role by its name (e.g. {@code ROLE_ADMIN}). */
	Optional<Role> findByName(String name);

}
