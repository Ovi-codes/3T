package ro.threet.run.auth;

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

/**
 * The site is public-first: browsing runs and registering for one stay anonymous (the core loop,
 * charter §3). Accounts add a small authenticated surface — {@code /api/auth/me} today, the
 * dashboard next increment. Authorisation is therefore deny-by-default with the public API
 * enumerated explicitly, so a new endpoint is closed until deliberately opened.
 *
 * Sessions are an httpOnly cookie (Spring Security's default {@code SecurityContextRepository}).
 * CSRF's token machinery is switched off for this JSON API: it is served same-origin and the
 * session cookie is SameSite=Lax (see application.yml), which blocks the cross-site form POST that
 * CSRF tokens defend against, without forcing a token round-trip onto the anonymous registration
 * POST. A token-based CSRF layer is a pre-go-live hardening item, tracked alongside the Entra swap.
 */
@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/registrations").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
						.requestMatchers("/api/ping").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.disable())
				// A protected endpoint hit without a session is a plain 401, not a redirect to a
				// (non-existent) login page — this is an API, the SPA owns the login UI.
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				// POST /api/auth/logout: drop the session, clear the context, expire the cookie, and
				// answer 204 — no redirect (the SPA handles what comes next).
				.logout(logout -> logout
						.logoutUrl("/api/auth/logout")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID")
						.logoutSuccessHandler((request, response, authentication) ->
								response.setStatus(HttpStatus.NO_CONTENT.value())));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Where the authenticated context is persisted between requests: the HTTP session. */
	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

}
