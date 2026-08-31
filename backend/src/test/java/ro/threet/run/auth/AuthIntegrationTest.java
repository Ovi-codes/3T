package ro.threet.run.auth;

import java.time.OffsetDateTime;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.email.EmailSender;
import ro.threet.run.event.EventRepository;
import ro.threet.run.registration.Registration;
import ro.threet.run.registration.RegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accounts proven end to end against real Postgres (Testcontainers), through the whole security
 * filter chain: sign-up creates a user and an authenticated session (CS-2); login opens a session
 * and wrong credentials are a 401 (CS-3); a taken email is refused from both an anonymous and a
 * signed-in caller; and a registration made while signed in carries the user's id (Task 3) while an
 * anonymous one does not. The confirmation email is stubbed — SMTP is exercised in the registration
 * integration test, not here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private EventRepository events;

	// Registration sends a confirmation email; stub it so the signed-in-registration test needs no
	// SMTP server.
	@MockitoBean
	private EmailSender emailSender;

	@BeforeEach
	void reset() {
		registrations.deleteAll(); // FK to app_user — clear children first
		users.deleteAll();
	}

	@Test
	void signupCreatesAUserAndAnAuthenticatedSession() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andExpect(jsonPath("$.id").isNumber())
				.andReturn();

		assertThat(users.count()).isEqualTo(1);

		// The session opened by sign-up is authenticated: /me returns the same account.
		mockMvc.perform(get("/api/auth/me").session(sessionOf(signup)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("ana@example.com"));
	}

	@Test
	void loginWithCorrectCredentialsOpensAnAuthenticatedSession() throws Exception {
		mockMvc.perform(signupRequest("ana@example.com", "correct horse")).andExpect(status().isCreated());

		MvcResult login = mockMvc.perform(loginRequest("ana@example.com", "correct horse"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andReturn();

		mockMvc.perform(get("/api/auth/me").session(sessionOf(login)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("ana@example.com"));
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		MockHttpSession session = sessionOf(signup);

		mockMvc.perform(post("/api/auth/logout").session(session))
				.andExpect(status().isNoContent());

		// The (now invalidated) session no longer authenticates.
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithWrongPasswordIsUnauthorised() throws Exception {
		mockMvc.perform(signupRequest("ana@example.com", "correct horse")).andExpect(status().isCreated());

		mockMvc.perform(loginRequest("ana@example.com", "wrong password"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errors.credentials").exists());
	}

	@Test
	void meWithoutASessionIsUnauthorised() throws Exception {
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void duplicateEmailIsRejectedForAnonymousAndSignedInCallersAlike() throws Exception {
		MvcResult first = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();

		// Anonymous retry.
		mockMvc.perform(signupRequest("ana@example.com", "another password"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errors.email").exists());

		// Retry from the signed-in session — same 409.
		mockMvc.perform(signupRequest("ana@example.com", "another password").session(sessionOf(first)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errors.email").exists());

		assertThat(users.count()).isEqualTo(1);
	}

	@Test
	void signedInRegistrationCarriesTheUserIdWhileAnonymousDoesNot() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) body(signup).get("id")).longValue();
		long eventId = firstUpcomingEventId();

		// Anonymous registration — no user attributed.
		mockMvc.perform(registrationRequest(eventId, "Guest", "guest@example.com"))
				.andExpect(status().isCreated());
		assertThat(registrationFor("guest@example.com").getUserId()).isNull();

		// Signed-in registration — attributed to the account.
		mockMvc.perform(registrationRequest(eventId, "Ana", "ana@example.com").session(sessionOf(signup)))
				.andExpect(status().isCreated());
		assertThat(registrationFor("ana@example.com").getUserId()).isEqualTo(userId);
	}

	private long firstUpcomingEventId() {
		return events.findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(OffsetDateTime.now())
				.get(0).getId();
	}

	private Registration registrationFor(String email) {
		return registrations.findAll().stream()
				.filter(registration -> registration.getEmail().equals(email))
				.findFirst()
				.orElseThrow();
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signupRequest(
			String email, String password) {
		return post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}""".formatted(email, password));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
			String email, String password) {
		return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}""".formatted(email, password));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registrationRequest(
			long eventId, String name, String email) {
		return post("/api/registrations").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"eventId": %d, "name": "%s", "email": "%s"}""".formatted(eventId, name, email));
	}

	private static MockHttpSession sessionOf(MvcResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private Map<String, Object> body(MvcResult result) throws Exception {
		return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
		});
	}

}
