package ro.threet.run.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Loads the CSRF token on every request, so the {@code XSRF-TOKEN} cookie is written on
 * the first response a visitor gets. Spring Security defers the token until something
 * reads it, and a JSON API never does — without this filter, a fresh visitor's first POST
 * (the anonymous registration) would have no cookie to echo back and would get a 403.
 * Runs right after {@code CsrfFilter}, which puts the deferred token on the request.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (token != null) {
			token.getToken();
		}
		chain.doFilter(request, response);
	}

}
