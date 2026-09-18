package ro.threet.run.auth;

import java.util.List;

/**
 * The account as the client sees it — id, email, name, and roles; no hash, nothing sensitive.
 * Returned by sign-up, login, and {@code /me} so the frontend can hold session state (prefill the
 * registration form from the name, and show admin-only controls from the roles) without a second
 * request. Roles are not sensitive personal data (GDPR §7); the real authorisation boundary is
 * server-side, so exposing them here only drives which controls the UI renders.
 */
public record AccountResponse(Long id, String email, String name, List<String> roles) {

	static AccountResponse from(AccountPrincipal principal) {
		return new AccountResponse(principal.id(), principal.email(), principal.name(),
				principal.roles().stream().sorted().toList());
	}

}
