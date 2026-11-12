package ro.threet.run.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import ro.threet.run.TestcontainersConfiguration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One container serves both halves of the app (ADR 0002): Spring hosts the Angular build
 * from {@code classpath:/static/} next to the API. These tests use a stand-in build from
 * the test resources: an {@code index.html} shell and one hashed chunk.
 *
 * A deep link to an Angular route (a page reload on {@code /dashboard}) must get the SPA
 * shell, not a 404 or a 401. The fallback must not leak into the API: an unknown
 * {@code /api} path stays a 404, deny-by-default still answers 401, and the admin
 * boundary still answers 403. A missing file (a stale chunk name) is a real 404, so the
 * browser doesn't parse HTML as JavaScript.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SpaHostingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@ValueSource(strings = { "/dashboard", "/login", "/register/42", "/leaderboard/7" })
	void anAngularRouteGetsTheSpaShellWithoutASession(String route) throws Exception {
		mockMvc.perform(get(route)).andExpect(status().isOk()).andExpect(content().string(containsString("spa-shell")));
	}

	/**
	 * Spring Boot's welcome page forwards {@code /} to {@code index.html}; MockMvc stops
	 * at the forward, a real server serves the shell.
	 */
	@Test
	void theRootForwardsToTheShell() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
	}

	/**
	 * The shell must be revalidated on every load: after a deploy, a cached copy would
	 * point at chunk names that no longer exist.
	 */
	@Test
	void theShellIsRevalidatedOnEveryLoad() throws Exception {
		mockMvc.perform(get("/dashboard")).andExpect(header().string("Cache-Control", "no-cache"));
	}

	@Test
	void aBuiltAssetIsServedAsItself() throws Exception {
		mockMvc.perform(get("/main-TESTHASH.js"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("chunk")));
	}

	@Test
	void aMissingAssetIsA404NotTheShell() throws Exception {
		mockMvc.perform(get("/main-OLDHASH.js")).andExpect(status().isNotFound());
	}

	@Test
	void anAnonymousApiCallIsStillA401() throws Exception {
		mockMvc.perform(get("/api/me/registrations")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/no-such-endpoint")).andExpect(status().isUnauthorized());
	}

	@Test
	void anUnknownApiPathIsA404NotTheShell() throws Exception {
		mockMvc.perform(get("/api/no-such-endpoint").with(user("ana@example.com")))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("spa-shell"))));
	}

	@Test
	void theAdminBoundaryStillHolds() throws Exception {
		mockMvc.perform(get("/api/admin/events")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/events").with(user("ana@example.com"))).andExpect(status().isForbidden());
	}

	@Test
	void actuatorStaysClosedExceptForHealth() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/env"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().string(not(containsString("spa-shell"))));
	}

}
