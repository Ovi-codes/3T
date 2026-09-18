package ro.threet.run.auth;

import java.util.Locale;

/**
 * One canonical form for an email across the auth package: trimmed and lower-cased. Storage and
 * lookup ({@link AppUserRepository#findByEmail}), sign-up/login ({@link LocalAuthProvider}) and
 * admin membership ({@link AdminEmails}) all normalise the same way, so an account is case- and
 * whitespace-insensitive on its address and the unique constraint does the deduping.
 */
final class Emails {

	private Emails() {
	}

	static String normalise(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
