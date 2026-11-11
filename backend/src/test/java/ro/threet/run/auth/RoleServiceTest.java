package ro.threet.run.auth;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The role-provisioning rule in isolation: every authenticated account gets
 * {@code ROLE_USER} as a baseline; an account whose email is named in {@link AdminEmails}
 * additionally gets {@code ROLE_ADMIN} lazily on authentication, and loses it once it is
 * no longer named; and a role already held is not written again. The SQL itself
 * (idempotent under concurrent logins) is proven on a live Postgres by
 * {@link ConcurrentRoleGrantIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

	private static final long USER_ID = 7L;

	private static final long USER_ROLE_ID = 1L;

	private static final long ADMIN_ROLE_ID = 2L;

	@Mock
	private RoleRepository roles;

	@Mock
	private AdminEmails adminEmails;

	private RoleService roleService() {
		lenient().when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(role(USER_ROLE_ID, "ROLE_USER")));
		lenient().when(roles.findByName("ROLE_ADMIN")).thenReturn(Optional.of(role(ADMIN_ROLE_ID, "ROLE_ADMIN")));
		return new RoleService(roles, adminEmails);
	}

	@Test
	void aRegularAccountGetsOnlyTheUserRole() {
		given(adminEmails.isAdmin("ana@example.com")).willReturn(false);
		given(roles.namesHeldBy(USER_ID)).willReturn(Set.of());

		Set<String> granted = roleService().ensureRolesFor(account("ana@example.com"));

		assertThat(granted).containsExactly("ROLE_USER");
		verify(roles).grant(USER_ID, USER_ROLE_ID);
		verify(roles, never()).grant(USER_ID, ADMIN_ROLE_ID);
		verify(roles, never()).revoke(anyLong(), anyString());
	}

	@Test
	void aConfiguredAdminEmailGetsBothAdminAndUserRoles() {
		given(adminEmails.isAdmin("boss@example.com")).willReturn(true);
		given(roles.namesHeldBy(USER_ID)).willReturn(Set.of());

		Set<String> granted = roleService().ensureRolesFor(account("boss@example.com"));

		assertThat(granted).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
		verify(roles).grant(USER_ID, USER_ROLE_ID);
		verify(roles).grant(USER_ID, ADMIN_ROLE_ID);
	}

	@Test
	void aRoleAlreadyHeldIsNotGrantedAgain() {
		given(adminEmails.isAdmin("boss@example.com")).willReturn(true);
		given(roles.namesHeldBy(USER_ID)).willReturn(Set.of("ROLE_USER")); // from an
																			// earlier
																			// sign-up

		Set<String> granted = roleService().ensureRolesFor(account("boss@example.com"));

		assertThat(granted).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
		verify(roles, never()).grant(USER_ID, USER_ROLE_ID);
		verify(roles).grant(USER_ID, ADMIN_ROLE_ID);
	}

	@Test
	void anAccountRemovedFromTheAdminConfigLosesTheAdminRoleOnReauthentication() {
		// Previously an admin (holds both roles from an earlier login), now dropped from
		// ADMIN_EMAILS.
		given(adminEmails.isAdmin("boss@example.com")).willReturn(false);
		given(roles.namesHeldBy(USER_ID)).willReturn(Set.of("ROLE_USER", "ROLE_ADMIN"));

		Set<String> granted = roleService().ensureRolesFor(account("boss@example.com"));

		assertThat(granted).containsExactly("ROLE_USER");
		verify(roles).revoke(USER_ID, "ROLE_ADMIN");
	}

	@Test
	void aSteadyStateAdminReauthenticatesWithoutAnyWrite() {
		// Still configured and already holding both roles — nothing to grant or revoke.
		given(adminEmails.isAdmin("boss@example.com")).willReturn(true);
		given(roles.namesHeldBy(USER_ID)).willReturn(Set.of("ROLE_USER", "ROLE_ADMIN"));

		Set<String> granted = roleService().ensureRolesFor(account("boss@example.com"));

		assertThat(granted).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
		verify(roles, never()).grant(anyLong(), anyLong());
		verify(roles, never()).revoke(anyLong(), anyString());
	}

	private static AppUser account(String email) {
		AppUser user = new AppUser(email, "Someone", "hash");
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private static Role role(long id, String name) {
		Role role = new Role(name);
		ReflectionTestUtils.setField(role, "id", id);
		return role;
	}

}
