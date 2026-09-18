package ro.threet.run.auth;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The role-provisioning rule in isolation: every authenticated account gets {@code ROLE_USER} as a
 * baseline; an account whose email is named in {@link AdminEmails} additionally gets
 * {@code ROLE_ADMIN} lazily on authentication; and the grant is idempotent (re-authenticating a user
 * who already holds a role adds nothing). Real join-row persistence is proven by the admin
 * integration test on a live Postgres.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

	private final Role userRole = new Role("ROLE_USER");
	private final Role adminRole = new Role("ROLE_ADMIN");

	@Mock
	private RoleRepository roles;

	@Mock
	private AppUserRepository users;

	@Mock
	private AdminEmails adminEmails;

	private RoleService roleService() {
		lenient().when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
		lenient().when(roles.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
		return new RoleService(roles, users, adminEmails);
	}

	@Test
	void aRegularAccountGetsOnlyTheUserRole() {
		given(adminEmails.isAdmin("ana@example.com")).willReturn(false);
		AppUser user = new AppUser("ana@example.com", "Ana Pop", "hash");

		Set<String> granted = roleService().ensureRolesFor(user);

		assertThat(granted).containsExactly("ROLE_USER");
		assertThat(user.getRoles()).extracting(Role::getName).containsExactly("ROLE_USER");
	}

	@Test
	void aConfiguredAdminEmailGetsBothAdminAndUserRoles() {
		given(adminEmails.isAdmin("boss@example.com")).willReturn(true);
		AppUser user = new AppUser("boss@example.com", "Boss", "hash");

		Set<String> granted = roleService().ensureRolesFor(user);

		assertThat(granted).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
		assertThat(user.getRoles()).extracting(Role::getName)
				.containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
	}

	@Test
	void grantingIsIdempotentWhenTheUserAlreadyHoldsTheRole() {
		given(adminEmails.isAdmin("ana@example.com")).willReturn(false);
		AppUser user = new AppUser("ana@example.com", "Ana Pop", "hash");
		user.addRole(userRole); // already provisioned by a previous login

		Set<String> granted = roleService().ensureRolesFor(user);

		assertThat(granted).containsExactly("ROLE_USER");
		assertThat(user.getRoles()).hasSize(1);
		// Nothing new to persist when the role is already held.
		verify(users, never()).save(any(AppUser.class));
	}

	@Test
	void aNewlyGrantedRoleIsPersisted() {
		given(adminEmails.isAdmin("boss@example.com")).willReturn(true);
		AppUser user = new AppUser("boss@example.com", "Boss", "hash");

		roleService().ensureRolesFor(user);

		// Two grants (USER + ADMIN) on a fresh account → the user is saved with its new join rows.
		verify(users, org.mockito.Mockito.atLeastOnce()).save(user);
	}

}
