package ro.threet.run.registration;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;
import ro.threet.run.location.LocationRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/me/stats} end to end against real Postgres (#45), through the whole
 * security filter chain. A signed-in user gets their placement and time on each finished
 * run, oldest first; the placement is ranked against everyone on that run (anonymous
 * runners included), but only the caller's own runs come back. An anonymous request is a
 * plain 401.
 *
 * <p>
 * Each test makes its own past runs and removes them afterwards, so the seeded schedule
 * other test classes count on (they share this cached context and its container) is left
 * as found.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MyStatsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private EventRepository events;

	@Autowired
	private LocationRepository locations;

	@Autowired
	private JdbcTemplate jdbc;

	private final List<Event> createdEvents = new ArrayList<>();

	@BeforeEach
	void reset() {
		// Children first (FK to app_user), then the accounts, so each test signs up fresh
		// emails.
		registrations.deleteAll();
		jdbc.update("delete from app_user");
	}

	@AfterEach
	void removeCreatedEvents() {
		registrations.deleteAll();
		events.deleteAll(createdEvents);
		createdEvents.clear();
	}

	@Test
	void anonymousRequestIsUnauthorised() throws Exception {
		mockMvc.perform(get("/api/me/stats")).andExpect(status().isUnauthorized());
	}

	@Test
	void givesPlacementAndTimeForEachFinishedRunOldestFirst() throws Exception {
		MvcResult ana = signUp("ana@example.com");
		long anaId = userIdOf(ana);

		Event older = aPastRun("Older run", 14);
		seed(older, "Bogdan Ionescu", null, 1300);
		seed(older, "Ana Pop", anaId, 1500);
		seed(older, "Carmen Dumitru", null, 1610);
		seed(older, "Elena Stan", null, null);

		Event newer = aPastRun("Newer run", 7);
		seed(newer, "Bogdan Ionescu", null, 1450);
		seed(newer, "Ana Pop", anaId, 1420);

		mockMvc.perform(get("/api/me/stats").session(sessionOf(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runs.length()").value(2))
			.andExpect(jsonPath("$.runs[0].eventId").value(older.getId()))
			.andExpect(jsonPath("$.runs[0].eventName").value("Older run"))
			.andExpect(jsonPath("$.runs[0].locationName").exists())
			.andExpect(jsonPath("$.runs[0].finishTimeSeconds").value(1500))
			.andExpect(jsonPath("$.runs[0].position").value(2))
			// Elena recorded no time, so she isn't one of the finishers.
			.andExpect(jsonPath("$.runs[0].finishers").value(3))
			.andExpect(jsonPath("$.runs[1].eventId").value(newer.getId()))
			.andExpect(jsonPath("$.runs[1].finishTimeSeconds").value(1420))
			.andExpect(jsonPath("$.runs[1].position").value(1))
			.andExpect(jsonPath("$.runs[1].finishers").value(2));
	}

	@Test
	void aTiedTimeSharesItsPlace() throws Exception {
		MvcResult ana = signUp("ana@example.com");

		Event run = aPastRun("Tied run", 7);
		seed(run, "Bogdan Ionescu", null, 1450);
		seed(run, "Ana Pop", userIdOf(ana), 1450);

		mockMvc.perform(get("/api/me/stats").session(sessionOf(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runs[0].position").value(1))
			.andExpect(jsonPath("$.runs[0].finishers").value(2));
	}

	@Test
	void showsOnlyTheCallersOwnRuns() throws Exception {
		MvcResult ana = signUp("ana@example.com");
		MvcResult bob = signUp("bob@example.com");

		Event shared = aPastRun("Shared run", 14);
		seed(shared, "Ana Pop", userIdOf(ana), 1500);
		seed(shared, "Bob Marin", userIdOf(bob), 1400);
		Event bobsOnly = aPastRun("Bob's run", 7);
		seed(bobsOnly, "Bob Marin", userIdOf(bob), 1380);

		// Bob's time still places him ahead of Ana on the run they shared, but his runs
		// aren't hers.
		mockMvc.perform(get("/api/me/stats").session(sessionOf(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runs.length()").value(1))
			.andExpect(jsonPath("$.runs[0].eventId").value(shared.getId()))
			.andExpect(jsonPath("$.runs[0].position").value(2));
	}

	@Test
	void leavesOutRunsWithoutATimeAndCancelledRuns() throws Exception {
		MvcResult ana = signUp("ana@example.com");
		long anaId = userIdOf(ana);

		Event finished = aPastRun("Finished run", 21);
		seed(finished, "Ana Pop", anaId, 1500);
		seed(aPastRun("No time run", 14), "Ana Pop", anaId, null);
		Event cancelled = aPastRun("Cancelled run", 7);
		seed(cancelled, "Ana Pop", anaId, 1400);
		cancelled.cancel();
		events.save(cancelled);

		mockMvc.perform(get("/api/me/stats").session(sessionOf(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runs.length()").value(1))
			.andExpect(jsonPath("$.runs[0].eventId").value(finished.getId()));
	}

	@Test
	void aUserWithNoFinishedRunsGetsAnEmptyList() throws Exception {
		MvcResult ana = signUp("ana@example.com");

		mockMvc.perform(get("/api/me/stats").session(sessionOf(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runs").isArray())
			.andExpect(jsonPath("$.runs.length()").value(0));
	}

	private Event aPastRun(String name, int daysAgo) {
		Event run = events
			.save(new Event(locations.findAll().getFirst(), name, OffsetDateTime.now().minusDays(daysAgo)));
		createdEvents.add(run);
		return run;
	}

	private Registration seed(Event run, String name, Long userId, Integer finishTimeSeconds) {
		Registration registration = new Registration(run, name, name.replace(' ', '.') + "@example.com");
		if (userId != null) {
			registration.linkUser(userId);
		}
		if (finishTimeSeconds != null) {
			registration.recordFinishTime(Duration.ofSeconds(finishTimeSeconds));
		}
		return registrations.save(registration);
	}

	private MvcResult signUp(String email) throws Exception {
		return mockMvc.perform(signupRequest(email)).andExpect(status().isCreated()).andReturn();
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

	private static MockHttpServletRequestBuilder signupRequest(String email) {
		return post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Ana Pop", "email": "%s", "password": "correct horse"}""".formatted(email));
	}

}
