package ro.threet.run.registration;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;
import ro.threet.run.location.LocationRepository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/events/{id}/leaderboard} end to end against real Postgres (#44), through the whole
 * security filter chain with no session — the leaderboard is public. Proves the DB ordering the
 * service relies on: fastest first, ties broken by who registered first, runners without a time last.
 *
 * <p>Each test gets its own past run, created here and removed afterwards, so the seeded schedule
 * other test classes count on (they share this cached context and its container) is left as found.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LeaderboardIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private EventRepository events;

	@Autowired
	private LocationRepository locations;

	private final List<Event> createdEvents = new ArrayList<>();

	@BeforeEach
	void reset() {
		registrations.deleteAll();
	}

	@AfterEach
	void removeCreatedEvents() {
		registrations.deleteAll();
		events.deleteAll(createdEvents);
		createdEvents.clear();
	}

	@Test
	void anonymousVisitorReadsARankedLeaderboard() throws Exception {
		Event run = aPastRun();
		seed(run, "Carmen Dumitru", 1610);
		seed(run, "Ana Pop", 1300);
		seed(run, "Bogdan Ionescu", 1450);

		mockMvc.perform(get("/api/events/" + run.getId() + "/leaderboard"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(run.getId()))
				.andExpect(jsonPath("$.eventName").value("Leaderboard test run"))
				.andExpect(jsonPath("$.locationName").value("Tineretului Park"))
				.andExpect(jsonPath("$.entries.length()").value(3))
				.andExpect(jsonPath("$.entries[0].position").value(1))
				.andExpect(jsonPath("$.entries[0].runnerName").value("Ana P."))
				.andExpect(jsonPath("$.entries[0].finishTimeSeconds").value(1300))
				.andExpect(jsonPath("$.entries[1].runnerName").value("Bogdan I."))
				.andExpect(jsonPath("$.entries[2].runnerName").value("Carmen D."))
				.andExpect(jsonPath("$.entries[2].position").value(3));
	}

	@Test
	void neverDisclosesEmailsOrFullNames() throws Exception {
		Event run = aPastRun();
		seed(run, "Ana Pop", 1300);

		mockMvc.perform(get("/api/events/" + run.getId() + "/leaderboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("@"))))
				.andExpect(content().string(not(containsString("Pop"))));
	}

	@Test
	void tiedTimesShareAPositionInRegistrationOrder() throws Exception {
		Event run = aPastRun();
		Registration first = seed(run, "Bogdan Ionescu", 1450);
		seed(run, "Carmen Dumitru", 1450);
		seed(run, "Ana Pop", 1300);
		// Re-save the first-registered runner last: Postgres writes the updated row after the others,
		// so only the explicit tie-break (not storage order) can put Bogdan ahead of Carmen.
		first.recordFinishTime(Duration.ofSeconds(1451));
		registrations.save(first);
		first.recordFinishTime(Duration.ofSeconds(1450));
		registrations.save(first);

		mockMvc.perform(get("/api/events/" + run.getId() + "/leaderboard"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries[0].runnerName").value("Ana P."))
				.andExpect(jsonPath("$.entries[1].runnerName").value("Bogdan I."))
				.andExpect(jsonPath("$.entries[1].position").value(2))
				.andExpect(jsonPath("$.entries[2].runnerName").value("Carmen D."))
				.andExpect(jsonPath("$.entries[2].position").value(2));
	}

	@Test
	void runnersWithoutATimeAreListedLast() throws Exception {
		Event run = aPastRun();
		seed(run, "Elena Stan", null);
		seed(run, "Ana Pop", 1300);

		mockMvc.perform(get("/api/events/" + run.getId() + "/leaderboard"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries[0].runnerName").value("Ana P."))
				.andExpect(jsonPath("$.entries[1].runnerName").value("Elena S."))
				.andExpect(jsonPath("$.entries[1].position").value(nullValue()))
				.andExpect(jsonPath("$.entries[1].finishTimeSeconds").value(nullValue()));
	}

	@Test
	void anUnknownRunIsNotFound() throws Exception {
		mockMvc.perform(get("/api/events/999999/leaderboard"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errors.eventId").exists());
	}

	@Test
	void anUpcomingRunHasNoLeaderboard() throws Exception {
		mockMvc.perform(get("/api/events/" + anUpcomingRun().getId() + "/leaderboard"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errors.eventId").exists());
	}

	@Test
	void aCancelledRunHasNoLeaderboard() throws Exception {
		Event run = aPastRun();
		run.cancel();
		events.save(run);

		mockMvc.perform(get("/api/events/" + run.getId() + "/leaderboard"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errors.eventId").exists());
	}

	private Event aPastRun() {
		Event run = new Event(locations.findAll().getFirst(), "Leaderboard test run",
				OffsetDateTime.now().minusDays(2));
		run = events.save(run);
		createdEvents.add(run);
		return run;
	}

	private Event anUpcomingRun() {
		OffsetDateTime now = OffsetDateTime.now();
		return events.findAll().stream()
				.filter(event -> !event.isCancelled() && event.getStartDateTime().isAfter(now))
				.min(Comparator.comparing(Event::getStartDateTime))
				.orElseThrow();
	}

	private Registration seed(Event run, String name, Integer finishTimeSeconds) {
		Registration registration = new Registration(run, name, name.replace(' ', '.') + "@example.com");
		if (finishTimeSeconds != null) {
			registration.recordFinishTime(Duration.ofSeconds(finishTimeSeconds));
		}
		return registrations.save(registration);
	}

}
