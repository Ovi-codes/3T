package ro.threet.run.auth;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoleRepository extends JpaRepository<Role, Long> {

	/** Look up a seeded role by its name (e.g. {@code ROLE_ADMIN}). */
	Optional<Role> findByName(String name);

	/**
	 * Give the account the role. A row that already exists is not an error: a concurrent login of the
	 * same account may have written it first (issue #64).
	 */
	@Modifying
	@Query(value = "insert into user_roles (user_id, role_id) values (:userId, :roleId) on conflict do nothing",
			nativeQuery = true)
	void grant(@Param("userId") Long userId, @Param("roleId") Long roleId);

	/** Take the role from the account. A row that is already gone is not an error. */
	@Modifying
	@Query(value = "delete from user_roles where user_id = :userId"
			+ " and role_id = (select id from role where name = :roleName)", nativeQuery = true)
	void revoke(@Param("userId") Long userId, @Param("roleName") String roleName);

	/** The names of the roles the account holds, as the table has them now. */
	@Query(value = "select r.name from user_roles ur join role r on r.id = ur.role_id where ur.user_id = :userId",
			nativeQuery = true)
	Set<String> namesHeldBy(@Param("userId") Long userId);

}
