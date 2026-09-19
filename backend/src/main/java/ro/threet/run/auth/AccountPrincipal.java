package ro.threet.run.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The app's notion of "who is signed in", decoupled from both the JPA entity and the identity
 * provider. This is what gets stored as the authenticated principal and what controllers read via
 * {@code @AuthenticationPrincipal}. A future Entra swap builds the same principal from an OIDC
 * token instead of the local {@link AppUser}, so nothing downstream changes.
 *
 * The {@code roles} are resolved at authentication time (see {@link RoleService}) and travel with
 * the principal, so authorities come straight from the session with no per-request DB lookup. Every
 * authenticated account carries {@code ROLE_USER}; a configured admin also carries {@code ROLE_ADMIN}.
 *
 * Serializable because it lives in the (session-backed) SecurityContext.
 */
public record AccountPrincipal(Long id, String email, String name, Set<String> roles)
		implements Serializable {

	static AccountPrincipal of(AppUser user, Set<String> roles) {
		return new AccountPrincipal(user.getId(), user.getEmail(), user.getName(), Set.copyOf(roles));
	}

	public Collection<? extends GrantedAuthority> authorities() {
		return roles.stream().map(SimpleGrantedAuthority::new).toList();
	}

}
