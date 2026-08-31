package ro.threet.run.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Turns a verified {@link AccountPrincipal} into a logged-in HTTP session: it builds the
 * authentication, stores it in a fresh {@link SecurityContext}, and persists that to the session so
 * the httpOnly cookie carries it on the next request. Kept out of {@link AuthProvider} on purpose —
 * this is the cookie-session mechanism, which an OIDC provider would replace wholesale.
 *
 * The session id is rotated on login to defend against session fixation (a pre-login session can't
 * be promoted to an authenticated one).
 */
@Component
class SessionAuthenticator {

	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

	SessionAuthenticator(SecurityContextRepository securityContextRepository) {
		this.securityContextRepository = securityContextRepository;
	}

	void establishSession(AccountPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}

		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
				principal, null, principal.authorities());
		SecurityContext context = holderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		holderStrategy.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}

}
