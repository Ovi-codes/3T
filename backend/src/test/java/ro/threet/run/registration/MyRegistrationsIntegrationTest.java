package ro.threet.run.registration;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

	@Test
	void recordsAFinishTimeOnTheUsersOwnPastRegistration() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		Registration registration = seedRegistration(aPastEvent(), userIdOf(signup), "ana@example.com");

		mockMvc.perform(finishTimeRequest(registration.getId(), 1471).session(sessionOf(signup)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registrationId").value(registration.getId()))
				.andExpect(jsonPath("$.finishTimeSeconds").value(1471));

		// The dashboard's Past row now carries the recorded time.
		mockMvc.perform(get("/api/me/registrations").session(sessionOf(signup)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.past[0].finishTimeSeconds").value(1471));
	}

	@Test
	void correctsAPreviouslyRecordedFinishTime() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		Registration registration = seedRegistration(aPastEvent(), userIdOf(signup), "ana@example.com");
		mockMvc.perform(finishTimeRequest(registration.getId(), 1471).session(sessionOf(signup)))
				.andExpect(status().isOk());

		mockMvc.perform(finishTimeRequest(registration.getId(), 1502).session(sessionOf(signup)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.finishTimeSeconds").value(1502));

		mockMvc.perform(get("/api/me/registrations").session(sessionOf(signup)))
				.andExpect(jsonPath("$.past[0].finishTimeSeconds").value(1502));
	}

	@Test
	void refusesAFinishTimeOnSomeoneElsesRegistration() throws Exception {
		MvcResult ana = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		MvcResult bob = mockMvc.perform(signupRequest("bob@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		Registration bobs = seedRegistration(aPastEvent(), userIdOf(bob), "bob@example.com");

		// Not found, not forbidden — Ana learns nothing about whether Bob's row exists.
		mockMvc.perform(finishTimeRequest(bobs.getId(), 1471).session(sessionOf(ana)))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/me/registrations").session(sessionOf(bob)))
				.andExpect(jsonPath("$.past[0].finishTimeSeconds").isEmpty());
	}

	@Test
	void refusesAFinishTimeForARunThatHasNotTakenPlace() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		Registration registration = seedRegistration(anUpcomingEvent(), userIdOf(signup), "ana@example.com");

		mockMvc.perform(finishTimeRequest(registration.getId(), 1471).session(sessionOf(signup)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.finishTimeSeconds").exists());
	}

	@Test
	void anonymousFinishTimeIsUnauthorised() throws Exception {
		mockMvc.perform(finishTimeRequest(1L, 1471)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 599, 3601 })
	void rejectsAFinishTimeOutsideTheSensibleRange(int finishTimeSeconds) throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		Registration registration = seedRegistration(aPastEvent(), userIdOf(signup), "ana@example.com");

		// 10:00 to 1:00:00 is the accepted window for a 5k.
		mockMvc.perform(finishTimeRequest(registration.getId(), finishTimeSeconds).session(sessionOf(signup)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.finishTimeSeconds").exists());
	}

	@Test
	void rejectsAMissingFinishTime() throws Exception {
		MvcResult signup = mockMvc.perform(signupRequest("ana@example.com", "correct horse"))
				.andExpect(status().isCreated())
				.andReturn();
		Registration registration = seedRegistration(aPastEvent(), userIdOf(signup), "ana@example.com");

		mockMvc.perform(put("/api/me/registrations/" + registration.getId() + "/finish-time")
				.session(sessionOf(signup)).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.finishTimeSeconds").exists());
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

	private Registration seedRegistration(Event event, long userId, String email) {
		Registration registration = new Registration(event, "Ana", email);
		registration.linkUser(userId);
		return registrations.save(registration);
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder finishTimeRequest(
			long registrationId, int finishTimeSeconds) {
		return put("/api/me/registrations/" + registrationId + "/finish-time")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"finishTimeSeconds": %d}""".formatted(finishTimeSeconds));
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
						{"name": "Ana Pop", "email": "%s", "password": "%s"}""".formatted(email, password));
	}

}
