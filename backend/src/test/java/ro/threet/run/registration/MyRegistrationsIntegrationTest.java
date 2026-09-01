package ro.threet.run.registration;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard's data source proven end to end against real Postgres (Testcontainers), through the
 * whole security filter chain: a signed-in user's registration for a future run shows under Upcoming
 * (CS-4) and one for a run whose date has passed shows under Past (CS-5); an anonymous request is a
 * plain 401 (CS-6, server side). Past rows are seeded straight through the repository — the public
 * registration endpoint refuses past events, but "past" here means the run has since gone by.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MyRegistrationsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private EventRepository events;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void reset() {
		// Children first (FK to app_user), then the accounts — so each test signs up fresh emails
		// without tripping the unique-email rule against a prior test's rows.
		registrations.deleteAll();
		jdbc.update("delete from app_user");
	}

	@Test
	void anonymousRequestIsUnauthorised() throws Exception {
		mockMvc.perform(get("/api/me/registrations")).andExpect(status().isUnauthorized());
	}

	@Test
	void splitsTheUsersRegistrationsIntoUpcomingAndPast() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = userIdOf(signup);

		Event upcoming = anUpcomingEvent();
		Event past = aPastEvent();
		seedRegistration(upcoming, userId, "ana@example.com");
		seedRegistration(past, userId, "ana@example.com");

		mockMvc.perform(get("/api/me/registrations").session(sessionOf(signup)))
				.andExpect(status().isOk())
				// The future run is under Upcoming, the gone-by one under Past — each in its own bucket.
				.andExpect(jsonPath("$.upcoming.length()").value(1))
				.andExpect(jsonPath("$.upcoming[0].eventId").value(upcoming.getId()))
				.andExpect(jsonPath("$.past.length()").value(1))
				.andExpect(jsonPath("$.past[0].eventId").value(past.getId()));
	}

	@Test
	void showsOnlyTheCurrentUsersRegistrations() throws Exception {
		MvcResult ana = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		MvcResult bob = mockMvc.perform(signupRequest("bob@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();

		Event upcoming = anUpcomingEvent();
		seedRegistration(upcoming, userIdOf(bob), "bob@example.com");

		// Ana has no registrations of her own — Bob's must not leak into her dashboard.
		mockMvc.perform(get("/api/me/registrations").session(sessionOf(ana)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upcoming.length()").value(0))
				.andExpect(jsonPath("$.past.length()").value(0));
	}

	private Event anUpcomingEvent() {
		OffsetDateTime now = OffsetDateTime.now();
		return events.findAll().stream()
				.filter(event -> !event.getStartDateTime().isBefore(now))
				.min(Comparator.comparing(Event::getStartDateTime))
				.orElseThrow();
	}

	private Event aPastEvent() {
		OffsetDateTime now = OffsetDateTime.now();
		return events.findAll().stream()
				.filter(event -> event.getStartDateTime().isBefore(now))
				.max(Comparator.comparing(Event::getStartDateTime))
				.orElseThrow();
	}

	private void seedRegistration(Event event, long userId, String email) {
		Registration registration = new Registration(event, "Ana", email);
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

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signupRequest(
			String email, String password) {
		return post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}""".formatted(email, password));
	}

}
