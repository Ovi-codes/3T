package ro.threet.run.event;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.email.EmailSender;
import ro.threet.run.registration.RegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin create-event slice, proven end to end against real Postgres (Testcontainers) through the
 * whole security filter chain (issue #57):
 *
 *  - a configured {@code ADMIN_EMAILS} account carries {@code ROLE_ADMIN} + {@code ROLE_USER} on
 *    authentication, and a regular account carries only {@code ROLE_USER};
 *  - an admin creates a valid event and it turns up on {@code GET /api/events};
 *  - a non-admin is refused (403) and an anonymous caller is refused (401) — the real boundary is
 *    server-side, not the hidden UI;
 *  - an invalid submission (blank name, past start) is a 400 and nothing is created.
 *
 * {@code app.admin.emails} is set for the test so {@code admin@example.com} is the admin.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.admin.emails=admin@example.com")
@AutoConfigureMockMvc
class AdminEventIntegrationTest {

	private static final String ADMIN_EMAIL = "admin@example.com";
	private static final String USER_EMAIL = "ana@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private EventRepository events;

	@Autowired
	private JdbcTemplate jdbc;

	// Nothing in this slice sends email; stub it so the context needs no SMTP server.
	@MockitoBean
	private EmailSender emailSender;

	@BeforeEach
	void reset() {
		// Children first (FK to app_user), then the accounts — so each test signs up fresh emails
		// without tripping the unique-email rule against a prior test's rows. Cleared via JdbcTemplate
		// so this event-package test needs no cross-package handle on the auth repository.
		registrations.deleteAll();
		jdbc.update("delete from user_roles");
		jdbc.update("delete from app_user");
	}

	@Test
	void aConfiguredAdminAuthenticatesWithAdminAndUserRoles() throws Exception {
		mockMvc.perform(signup(ADMIN_EMAIL, "Boss"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_ADMIN", "ROLE_USER")));
	}

	@Test
	void aRegularAccountAuthenticatesWithOnlyTheUserRole() throws Exception {
		mockMvc.perform(signup(USER_EMAIL, "Ana Pop"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_USER")));
	}

	@Test
	void adminCreatesAnEventAndItAppearsOnTheEventsList() throws Exception {
		MockHttpSession admin = sessionOf(mockMvc.perform(signup(ADMIN_EMAIL, "Boss"))
				.andExpect(status().isCreated()).andReturn());

		String futureLocal = LocalDateTime.now().plusDays(30).withHour(9).withMinute(0)
				.withSecond(0).withNano(0).toString();

		mockMvc.perform(createEvent(admin, "Autumn 5k", futureLocal))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("Autumn 5k"))
				.andExpect(jsonPath("$.locationName").value("Tineretului Park"))
				.andExpect(jsonPath("$.city").value("Bucharest"));

		// It is now among the upcoming events served publicly.
		mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Autumn 5k')]").exists());
	}

	@Test
	void nonAdminIsForbiddenFromCreating() throws Exception {
		MockHttpSession user = sessionOf(mockMvc.perform(signup(USER_EMAIL, "Ana Pop"))
				.andExpect(status().isCreated()).andReturn());
		long before = events.count();

		mockMvc.perform(createEvent(user, "Sneaky run", futureLocal()))
				.andExpect(status().isForbidden());

		assertThat(events.count()).isEqualTo(before);
	}

	@Test
	void anonymousIsUnauthorisedForCreating() throws Exception {
		// Anonymous hits the deny-by-default 401 (this API has no login page to redirect to); an
		// authenticated non-admin gets 403. Either way nothing is created.
		long before = events.count();

		mockMvc.perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sneaky run", "startDateTime": "%s"}""".formatted(futureLocal())))
				.andExpect(status().isUnauthorized());

		assertThat(events.count()).isEqualTo(before);
	}

	@Test
	void aBlankNameIsRejectedAndNothingIsCreated() throws Exception {
		MockHttpSession admin = sessionOf(mockMvc.perform(signup(ADMIN_EMAIL, "Boss"))
				.andExpect(status().isCreated()).andReturn());
		long before = events.count();

		mockMvc.perform(createEvent(admin, "   ", futureLocal()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(events.count()).isEqualTo(before);
	}

	@Test
	void aPastStartIsRejectedAndNothingIsCreated() throws Exception {
		MockHttpSession admin = sessionOf(mockMvc.perform(signup(ADMIN_EMAIL, "Boss"))
				.andExpect(status().isCreated()).andReturn());
		long before = events.count();

		String pastLocal = LocalDateTime.now().minusDays(1).withSecond(0).withNano(0).toString();

		mockMvc.perform(createEvent(admin, "Yesterday's run", pastLocal))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.startDateTime").exists());

		assertThat(events.count()).isEqualTo(before);
	}

	private static String futureLocal() {
		return LocalDateTime.now().plusDays(30).withHour(9).withMinute(0).withSecond(0).withNano(0)
				.toString();
	}

	private static MockHttpServletRequestBuilder signup(String email, String name) {
		return post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "email": "%s", "password": "correct horse battery"}"""
						.formatted(name, email));
	}

	private static MockHttpServletRequestBuilder createEvent(MockHttpSession session, String name,
			String startDateTime) {
		return post("/api/admin/events").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "startDateTime": "%s"}""".formatted(name, startDateTime));
	}

	private static MockHttpSession sessionOf(MvcResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
