package ro.threet.run.auth;

/**
 * The seam that keeps authentication swappable (charter §3): everything above this interface deals
 * in {@link AccountPrincipal}, never in the local users table or BCrypt. Today the only
 * implementation is {@link LocalAuthProvider} (email + password); migrating to Microsoft Entra
 * External ID means a new implementation here, not changes across the app.
 *
 * This interface is credential logic only — creating and verifying accounts. Establishing the
 * HTTP session is a web-layer concern and lives outside the seam, so an OIDC provider (which
 * manages its own session) can drop in without inheriting a cookie-session assumption.
 */
public interface AuthProvider {

	/**
	 * Create an account for the given credentials and return its principal.
	 *
	 * @throws EmailAlreadyUsedException if an account already exists for the email
	 */
	AccountPrincipal signup(String email, String rawPassword);

	/**
	 * Verify credentials and return the principal.
	 *
	 * @throws org.springframework.security.authentication.BadCredentialsException
	 *         if the email is unknown or the password does not match
	 */
	AccountPrincipal login(String email, String rawPassword);

}
