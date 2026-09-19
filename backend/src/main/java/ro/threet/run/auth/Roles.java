package ro.threet.run.auth;

/**
 * The role authorities the app knows about, kept as constants so the names live in one place
 * - a typo fails to compile rather than silently mis-authorising.
 *
 * <p>Spring's {@code hasRole("ADMIN")} re-adds the {@code ROLE_} prefix it strips, so {@link #ADMIN}
 * holds the bare name for that call, while {@link #ROLE_ADMIN} / {@link #ROLE_USER} are the full
 * authority names stored in {@code user_roles} and read into the principal.
 */
final class Roles {

	/** Full authority name of the baseline role every authenticated account carries. */
	static final String ROLE_USER = "ROLE_USER";

	/** Full authority name of the admin role, conferred by {@code ADMIN_EMAILS}. */
	static final String ROLE_ADMIN = "ROLE_ADMIN";

	/** The admin role without the {@code ROLE_} prefix, for {@code HttpSecurity.hasRole(...)}. */
	static final String ADMIN = "ADMIN";

	private Roles() {
	}

}
