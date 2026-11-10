package ro.threet.run.auth;

import java.util.Set;
import java.util.TreeSet;

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
	private final AdminEmails adminEmails;

	RoleService(RoleRepository roles, AdminEmails adminEmails) {
		this.roles = roles;
		this.adminEmails = adminEmails;
	}

	/**
	 * Reconcile the account's roles against config, then return the full set of role names for the
	 * principal. {@code ROLE_USER} is always granted; {@code ROLE_ADMIN} is granted iff the email is
	 * configured and revoked otherwise. A steady-state login only reads: an unchanged role is neither
	 * re-written nor re-removed.
	 *
	 * <p>Safe when the same account logs in twice at once (issue #64): both logins may see a role as
	 * missing, and both grant it, but the grant and the revoke are idempotent in SQL, so the second
	 * one is a no-op rather than a duplicate-key error.
	 */
	@Transactional
	Set<String> ensureRolesFor(AppUser user) {
		Set<String> held = new TreeSet<>(roles.namesHeldBy(user.getId()));
		grant(user, held, Roles.ROLE_USER);
		if (adminEmails.isAdmin(user.getEmail())) {
			grant(user, held, Roles.ROLE_ADMIN);
		} else {
			revoke(user, held, Roles.ROLE_ADMIN);
		}
		return held;
	}

	private void grant(AppUser user, Set<String> held, String roleName) {
		if (held.contains(roleName)) {
			return;
		}
		Role role = roles.findByName(roleName)
				.orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
		roles.grant(user.getId(), role.getId());
		held.add(roleName);
	}

	private void revoke(AppUser user, Set<String> held, String roleName) {
		if (!held.contains(roleName)) {
			return;
		}
		roles.revoke(user.getId(), roleName);
		held.remove(roleName);
	}

}
