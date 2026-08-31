package ro.threet.run.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The app's notion of "who is signed in", decoupled from both the JPA entity and the identity
 * provider. This is what gets stored as the authenticated principal and what controllers read via
 * {@code @AuthenticationPrincipal}. A future Entra swap builds the same principal from an OIDC
 * token instead of the local {@link AppUser}, so nothing downstream changes.
 *
 * Serializable because it lives in the (session-backed) SecurityContext.
 */
public record AccountPrincipal(Long id, String email) implements Serializable {

	static AccountPrincipal of(AppUser user) {
		return new AccountPrincipal(user.getId(), user.getEmail());
	}

	public Collection<? extends GrantedAuthority> authorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

}
