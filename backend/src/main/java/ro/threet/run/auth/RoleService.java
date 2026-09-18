package ro.threet.run.auth;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lazily provisions and resolves an account's roles at authentication time (issue #57). Every
 * authenticated account carries {@code ROLE_USER} as a baseline; an account whose email is
 * configured in {@link AdminEmails} additionally carries {@code ROLE_ADMIN}. Missing role rows are
 * created on the fly, so a fresh admin gets its {@code ROLE_ADMIN} the first time it signs up or logs
 * in — no separate provisioning step.
 *
 * <p>{@code ADMIN_EMAILS} is the single source of truth for {@code ROLE_ADMIN}, reconciled on every
 * authentication: an account no longer named there has its {@code ROLE_ADMIN} <em>revoked</em> the
 * next time it authenticates. So removing someone from the config, then their session expiring, is
 * all it takes to demote them — no manual row surgery.
 *
 * <p>This is the local provider's role resolution. A future Entra provider resolves roles from a
 * token claim instead (mapped to these same authorities via oid+tid), so the logic sits beside the
 * {@link LocalAuthProvider} rather than in the shared seam.
 */
@Service
class RoleService {

	private final RoleRepository roles;
	private final AppUserRepository users;
	private final AdminEmails adminEmails;

	RoleService(RoleRepository roles, AppUserRepository users, AdminEmails adminEmails) {
		this.roles = roles;
		this.users = users;
		this.adminEmails = adminEmails;
	}

	/**
	 * Reconcile the account's roles against config, then return the full set of role names for the
	 * principal. {@code ROLE_USER} is always granted; {@code ROLE_ADMIN} is granted iff the email is
	 * configured and revoked otherwise. Idempotent: an unchanged role is neither re-persisted nor
	 * re-removed, so a steady-state login writes nothing.
	 */
	@Transactional
	Set<String> ensureRolesFor(AppUser user) {
		grant(user, "ROLE_USER");
		if (adminEmails.isAdmin(user.getEmail())) {
			grant(user, "ROLE_ADMIN");
		} else {
			revoke(user, "ROLE_ADMIN");
		}
		return user.getRoles().stream().map(Role::getName)
				.collect(Collectors.toCollection(TreeSet::new));
	}

	private void grant(AppUser user, String roleName) {
		boolean alreadyHeld = user.getRoles().stream().anyMatch(role -> role.getName().equals(roleName));
		if (alreadyHeld) {
			return;
		}
		Role role = roles.findByName(roleName)
				.orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
		user.addRole(role);
		users.save(user);
	}

	private void revoke(AppUser user, String roleName) {
		boolean held = user.getRoles().stream().anyMatch(role -> role.getName().equals(roleName));
		if (!held) {
			return;
		}
		user.removeRole(roleName);
		users.save(user);
	}

}
