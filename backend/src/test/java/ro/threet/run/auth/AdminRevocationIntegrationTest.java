package ro.threet.run.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.email.EmailSender;
import ro.threet.run.registration.RegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin revocation, proven end to end against real Postgres (Testcontainers): {@code ADMIN_EMAILS}
 * is the source of truth, so an account that used to be an admin but is no longer named in the config
 * loses its {@code ROLE_ADMIN} the next time it authenticates — both in the returned principal and in
 * the {@code user_roles} table. The config here is empty (no admins), and a prior admin grant is
 * seeded directly so authentication has something to revoke.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.admin.emails=")
@AutoConfigureMockMvc
class AdminRevocationIntegrationTest {

	private static final String EMAIL = "demoted@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private JdbcTemplate jdbc;

	// This slice sends no email; stub it so the context needs no SMTP server.
	@MockitoBean
	private EmailSender emailSender;

	@BeforeEach
	void reset() {
		registrations.deleteAll(); // FK to app_user — clear children first
		jdbc.update("delete from user_roles");
		jdbc.update("delete from app_user");
	}

	@Test
	void anAccountDroppedFromAdminEmailsLosesRoleAdminOnNextLogin() throws Exception {
		// A plain account (config names no admins, so sign-up grants only ROLE_USER).
		mockMvc.perform(signup(EMAIL)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_USER")));

		// Simulate a previous life as an admin: seed the ROLE_ADMIN join row directly.
		grantAdminDirectly(EMAIL);
		assertThat(adminRowCount(EMAIL)).isEqualTo(1);

		// Re-authenticating reconciles against the (now admin-less) config: ROLE_ADMIN is revoked.
		mockMvc.perform(login(EMAIL)).andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_USER")));

		// The demotion is durable, not just cosmetic on the principal — the join row is gone.
		assertThat(adminRowCount(EMAIL)).isZero();
	}

	private void grantAdminDirectly(String email) {
		Long userId = jdbc.queryForObject("select id from app_user where email = ?", Long.class, email);
		Long adminRoleId = jdbc.queryForObject("select id from role where name = 'ROLE_ADMIN'", Long.class);
		jdbc.update("insert into user_roles (user_id, role_id) values (?, ?)", userId, adminRoleId);
	}

	private Integer adminRowCount(String email) {
		return jdbc.queryForObject("""
				select count(*) from user_roles ur
				join app_user u on u.id = ur.user_id
				join role r on r.id = ur.role_id
				where u.email = ? and r.name = 'ROLE_ADMIN'""", Integer.class, email);
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(String email) {
		return post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Ex Admin", "email": "%s", "password": "correct horse battery"}"""
						.formatted(email));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email) {
		return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "correct horse battery"}""".formatted(email));
	}

}
