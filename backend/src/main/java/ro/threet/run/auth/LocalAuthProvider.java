package ro.threet.run.auth;

import java.util.Set;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email + BCrypt implementation of the {@link AuthProvider} seam, backed by the local
 * {@code app_user} table. Emails are normalised (trimmed + lower-cased) so sign-up and login are
 * case-insensitive and the unique constraint does the deduping. Passwords are only ever stored as
 * a BCrypt hash; verification compares against that hash and never reveals which half failed.
 *
 * Role provisioning is delegated to {@link RoleService}: on both sign-up and login the account's
 * roles are lazily ensured and put onto the returned principal (so a configured admin gets its
 * {@code ROLE_ADMIN} the first time it authenticates). Login is therefore no longer read-only — it
 * may write a role row on that first admin sign-in.
 */
@Component
class LocalAuthProvider implements AuthProvider {

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final RoleService roleService;

	LocalAuthProvider(AppUserRepository users, PasswordEncoder passwordEncoder, RoleService roleService) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.roleService = roleService;
	}

	@Override
	@Transactional
	public AccountPrincipal signup(String email, String name, String rawPassword) {
		String normalised = Emails.normalise(email);
		if (users.existsByEmail(normalised)) {
			throw new EmailAlreadyUsedException("An account already exists for this email.");
		}
		// Email is normalised (the unique constraint dedupes on it); the name keeps its own casing,
		// trimmed of surrounding whitespace.
		AppUser saved = users.save(new AppUser(normalised, name.trim(), passwordEncoder.encode(rawPassword)));
		Set<String> roles = roleService.ensureRolesFor(saved);
		return AccountPrincipal.of(saved, roles);
	}

	@Override
	@Transactional
	public AccountPrincipal login(String email, String rawPassword) {
		// One generic failure for "unknown email" and "wrong password" alike — don't disclose
		// whether an address has an account.
		AppUser user = users.findByEmail(Emails.normalise(email))
				.filter(candidate -> passwordEncoder.matches(rawPassword, candidate.getPasswordHash()))
				.orElseThrow(() -> new BadCredentialsException("Email or password is incorrect."));
		Set<String> roles = roleService.ensureRolesFor(user);
		return AccountPrincipal.of(user, roles);
	}

}
