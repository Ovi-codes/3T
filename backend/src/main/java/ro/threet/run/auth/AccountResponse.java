package ro.threet.run.auth;

/**
 * The account as the client sees it — id, email, and name, no hash, nothing sensitive. Returned by
 * sign-up, login, and {@code /me} so the frontend can hold session state (and prefill the
 * registration form from the name) without a second request.
 */
public record AccountResponse(Long id, String email, String name) {

	static AccountResponse from(AccountPrincipal principal) {
		return new AccountResponse(principal.id(), principal.email(), principal.name());
	}

}
