package ro.threet.run.auth;

/**
 * The account as the client sees it — id and email, no hash, nothing sensitive. Returned by
 * sign-up, login, and {@code /me} so the frontend can hold session state without a second request.
 */
public record AccountResponse(Long id, String email) {

	static AccountResponse from(AccountPrincipal principal) {
		return new AccountResponse(principal.id(), principal.email());
	}

}
