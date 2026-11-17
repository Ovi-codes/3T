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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The backend is an API only (ADR 0003). The Angular build is a separate image, and the
 * proxy in front sends only {@code /api/*} and {@code /actuator/*} here. A path outside
 * those is therefore not a page: it gets the deny-by-default 401, like any closed path.
 * The API boundary must hold as before: an unknown {@code /api} path is a 401 for an
 * anonymous caller and a 404 for a signed-in one, the admin boundary answers 403, and
 * actuator is closed except for health.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NonApiPathsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@ValueSource(strings = { "/", "/dashboard", "/index.html", "/main-ABCD1234.js" })
	void aPathOutsideTheApiIsClosed(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
	}

	@Test
	void aSignedInUserGetsNoStaticFilesEither() throws Exception {
		mockMvc.perform(get("/index.html").with(user("ana@example.com"))).andExpect(status().isNotFound());
	}

	@Test
	void anAnonymousApiCallIsA401() throws Exception {
		mockMvc.perform(get("/api/me/registrations")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/no-such-endpoint")).andExpect(status().isUnauthorized());
	}

	@Test
	void anUnknownApiPathIsA404ForASignedInUser() throws Exception {
		mockMvc.perform(get("/api/no-such-endpoint").with(user("ana@example.com"))).andExpect(status().isNotFound());
	}

	@Test
	void theAdminBoundaryHolds() throws Exception {
		mockMvc.perform(get("/api/admin/events")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/events").with(user("ana@example.com"))).andExpect(status().isForbidden());
	}

	@Test
	void actuatorIsClosedExceptForHealth() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}

}
