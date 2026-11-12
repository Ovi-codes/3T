package ro.threet.run.auth;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * The site is public-first: browsing runs and registering for one stay anonymous (the
 * core loop, charter §3). Accounts add a small authenticated surface —
 * {@code /api/auth/me} and the dashboard's {@code /api/me/registrations}, and the user's
 * own GDPR data controls ({@code GET /api/me/export}, {@code DELETE /api/me}).
 * Authorisation is therefore deny-by-default with the public API enumerated explicitly,
 * so a new endpoint (like those) is closed until deliberately opened — an anonymous hit
 * is a plain 401.
 *
 * Sessions are an httpOnly cookie (Spring Security's default
 * {@code SecurityContextRepository}), SameSite=Lax (see application.yml). On top of that,
 * every state-changing request must carry a CSRF token (double-submit cookie, the
 * {@code csrf.spa()} setup): the server sets a JS-readable {@code XSRF-TOKEN} cookie, and
 * the SPA echoes it back in the {@code X-XSRF-TOKEN} header (Angular's {@code HttpClient}
 * does this by itself for relative URLs). A request without a matching token is a 403 —
 * that includes the anonymous registration POST, so a cross-site page can't register
 * someone on a visitor's behalf. {@link CsrfCookieFilter} makes sure the cookie is set on
 * the very first response, so a fresh visitor can register without an extra round-trip;
 * login and logout each swap the token for a fresh one (see
 * {@link SessionAuthenticator}).
 */
@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository)
			throws Exception {
		// The servlet ERROR dispatch only renders a status already decided (e.g. the CSRF
		// 403). Without this, deny-by-default turns any error an anonymous caller hits
		// into a 401.
		http.authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/api/events/**")
			.permitAll()
			.requestMatchers(HttpMethod.POST, "/api/registrations")
			.permitAll()
			.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login")
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/api/auth/csrf")
			.permitAll()
			.requestMatchers("/actuator/health")
			.permitAll()
			// Admin surface (issue #57): the real boundary is here, not the hidden UI. An
			// authenticated non-admin gets 403; an anonymous caller gets the
			// deny-by-default
			// 401 above (this API has no login page to redirect to).
			.requestMatchers("/api/admin/**")
			.hasRole(Roles.ADMIN)
			.anyRequest()
			.authenticated())
			// The token cookie is the one the repository bean below writes, so login can
			// rotate the same cookie (SessionAuthenticator).
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
			// A protected endpoint hit without a session is a plain 401, not a redirect
			// to a
			// (non-existent) login page — this is an API, the SPA owns the login UI.
			.exceptionHandling(
					handling -> handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			// No saved-request cache: there is no login redirect to replay, and caching
			// the request on a 401 would open a session (and set JSESSIONID) for every
			// anonymous visitor. Sessions start at sign-in only.
			.requestCache(cache -> cache.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			// POST /api/auth/logout: drop the session, clear the context, expire the
			// cookie, and answer 204 — no redirect (the SPA handles what comes next).
			// Spring's CSRF logout handler drops the old token; the success handler
			// then issues a fresh one in the same response, so signing straight back in
			// (with no GET in between) still carries a valid token.
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
				.logoutSuccessHandler((request, response, authentication) -> {
					csrfTokenRepository.loadDeferredToken(request, response).get();
					response.setStatus(HttpStatus.NO_CONTENT.value());
				}));
		return http.build();
	}

	/**
	 * The CSRF token store: an {@code XSRF-TOKEN} cookie that JavaScript can read (that
	 * is the point — the SPA copies it into the header). Secure follows the request, so
	 * it is Secure over TLS in prod and still works over plain http locally. SameSite=Lax
	 * to match the session cookie.
	 */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
		return repository;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Where the authenticated context is persisted between requests: the HTTP session.
	 */
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

}
