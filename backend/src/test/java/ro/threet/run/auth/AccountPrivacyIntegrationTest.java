package ro.threet.run.auth;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;
import ro.threet.run.registration.Registration;
import ro.threet.run.registration.RegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The user's GDPR data rights proven end to end against real Postgres (Testcontainers), through the
 * whole security filter chain (charter §7). A signed-in user can download everything held about
 * them ({@code GET /api/me/export}) and delete their account ({@code DELETE /api/me}); erasure takes
 * their registrations with it, ends the session, and leaves other accounts untouched. Both controls
 * are closed to anonymous callers (401).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountPrivacyIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AppUserRepository appUsers;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private EventRepository events;

	@BeforeEach
	void reset() {
		// Children first (FK to app_user), then the accounts — so each test signs up fresh emails
		// without tripping the unique-email rule against a prior test's rows.
		registrations.deleteAll();
		appUsers.deleteAll();
	}

	@Test
	void anonymousExportIsUnauthorised() throws Exception {
		mockMvc.perform(get("/api/me/export")).andExpect(status().isUnauthorized());
	}

	@Test
	void anonymousDeleteIsUnauthorised() throws Exception {
		mockMvc.perform(delete("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void exportReturnsTheAccountAndItsRegistrationsAsADownload() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = userIdOf(signup);
		seedRegistration(anUpcomingEvent(), userId, "Ana", "ana@example.com");

		mockMvc.perform(get("/api/me/export").session(sessionOf(signup)))
				.andExpect(status().isOk())
				// Served as a file the user can save, not an inline page.
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("threet-run-my-data.json")))
				.andExpect(jsonPath("$.account.email").value("ana@example.com"))
				.andExpect(jsonPath("$.account.id").value(userId))
				// The password hash is never part of the export.
				.andExpect(jsonPath("$.account.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.registrations.length()").value(1))
				.andExpect(jsonPath("$.registrations[0].participantName").value("Ana"))
				.andExpect(jsonPath("$.registrations[0].email").value("ana@example.com"));
	}

	@Test
	void eraseDeletesTheAccountAndItsRegistrationsAndEndsTheSession() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = userIdOf(signup);
		seedRegistration(anUpcomingEvent(), userId, "Ana", "ana@example.com");
		MockHttpSession session = sessionOf(signup);

		mockMvc.perform(delete("/api/me").session(session)).andExpect(status().isNoContent());

		assertThat(appUsers.findById(userId)).isEmpty();
		assertThat(registrations.findByUserIdWithEvent(userId)).isEmpty();
		// The session is torn down, so the deleted principal can't keep acting on it.
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void eraseLeavesOtherAccountsDataUntouched() throws Exception {
		MvcResult ana = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		MvcResult bob = mockMvc.perform(signupRequest("bob@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		Event event = anUpcomingEvent();
		seedRegistration(event, userIdOf(ana), "Ana", "ana@example.com");
		long bobId = userIdOf(bob);
		seedRegistration(event, bobId, "Bob", "bob@example.com");

		mockMvc.perform(delete("/api/me").session(sessionOf(ana))).andExpect(status().isNoContent());

		// Bob is a bystander — his account and his registration survive Ana's erasure.
		assertThat(appUsers.findById(bobId)).isPresent();
		assertThat(registrations.findByUserIdWithEvent(bobId)).hasSize(1);
	}

	private Event anUpcomingEvent() {
		OffsetDateTime now = OffsetDateTime.now();
		return events.findAll().stream()
				.filter(event -> !event.getStartDateTime().isBefore(now))
				.min(Comparator.comparing(Event::getStartDateTime))
				.orElseThrow();
	}

	private void seedRegistration(Event event, long userId, String name, String email) {
		Registration registration = new Registration(event, name, email);
		registration.linkUser(userId);
		registrations.save(registration);
	}

	private long userIdOf(MvcResult signup) throws Exception {
		Map<String, Object> body = objectMapper.readValue(signup.getResponse().getContentAsString(),
				new TypeReference<>() {
				});
		return ((Number) body.get("id")).longValue();
	}

	private static MockHttpSession sessionOf(MvcResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static MockHttpServletRequestBuilder signupRequest(String email, String password) {
		return post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}""".formatted(email, password));
	}

}
