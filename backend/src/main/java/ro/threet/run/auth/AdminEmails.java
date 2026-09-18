package ro.threet.run.auth;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The set of accounts that should hold {@code ROLE_ADMIN}, named by the {@code ADMIN_EMAILS} env var
 * (comma-separated). Emails are normalised (trimmed + lower-cased) the same way {@link
 * LocalAuthProvider} normalises them, so membership is case-insensitive. Empty by default, so a
 * plain dev or prod deploy has no admins until the var is set (charter Azure config: no hardcoding).
 */
@Component
class AdminEmails {

	private final Set<String> emails;

	AdminEmails(@Value("${app.admin.emails:}") String configured) {
		this.emails = Arrays.stream(configured.split(","))
				.map(AdminEmails::normalise)
				.filter(email -> !email.isBlank())
				.collect(Collectors.toUnmodifiableSet());
	}

	/** True when the given email is configured as an admin. */
	boolean isAdmin(String email) {
		return email != null && emails.contains(normalise(email));
	}

	private static String normalise(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
