package ro.threet.run.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounts (charter §3, CS-2/CS-3). Sign-up and login verify credentials through the
 * {@link AuthProvider} seam, then {@link SessionAuthenticator} promotes the result to a logged-in
 * cookie session; {@code /me} reads it back. The controller never touches the users table or
 * BCrypt directly, so the Entra swap stays a provider change.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final AuthProvider authProvider;
	private final SessionAuthenticator sessionAuthenticator;

	AuthController(AuthProvider authProvider, SessionAuthenticator sessionAuthenticator) {
		this.authProvider = authProvider;
		this.sessionAuthenticator = sessionAuthenticator;
	}

	/** CS-2: create the account and log the new user straight in. */
	@PostMapping("/signup")
	public ResponseEntity<AccountResponse> signup(@Valid @RequestBody SignupRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		AccountPrincipal principal = authProvider.signup(request.email(), request.password());
		sessionAuthenticator.establishSession(principal, httpRequest, httpResponse);
		return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(principal));
	}

	/** CS-3: verify credentials and open a session; wrong creds are a 401 from the provider. */
	@PostMapping("/login")
	public AccountResponse login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		AccountPrincipal principal = authProvider.login(request.email(), request.password());
		sessionAuthenticator.establishSession(principal, httpRequest, httpResponse);
		return AccountResponse.from(principal);
	}

	/** The current account, or 401 if the session isn't authenticated (enforced by the filter chain). */
	@GetMapping("/me")
	public AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
		return AccountResponse.from(principal);
	}

}
