package ro.threet.run.auth;

import java.util.Locale;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email + BCrypt implementation of the {@link AuthProvider} seam, backed by the local
 * {@code app_user} table. Emails are normalised (trimmed + lower-cased) so sign-up and login are
 * case-insensitive and the unique constraint does the deduping. Passwords are only ever stored as
 * a BCrypt hash; verification compares against that hash and never reveals which half failed.
 */
@Component
class LocalAuthProvider implements AuthProvider {

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;

	LocalAuthProvider(AppUserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public AccountPrincipal signup(String email, String rawPassword) {
		String normalised = normalise(email);
		if (users.existsByEmail(normalised)) {
			throw new EmailAlreadyUsedException("An account already exists for this email.");
		}
		AppUser saved = users.save(new AppUser(normalised, passwordEncoder.encode(rawPassword)));
		return AccountPrincipal.of(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public AccountPrincipal login(String email, String rawPassword) {
		// One generic failure for "unknown email" and "wrong password" alike — don't disclose
		// whether an address has an account.
		AppUser user = users.findByEmail(normalise(email))
				.filter(candidate -> passwordEncoder.matches(rawPassword, candidate.getPasswordHash()))
				.orElseThrow(() -> new BadCredentialsException("Email or password is incorrect."));
		return AccountPrincipal.of(user);
	}

	private static String normalise(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
