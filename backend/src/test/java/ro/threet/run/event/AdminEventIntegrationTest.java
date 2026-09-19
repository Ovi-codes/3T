package ro.threet.run.event;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.registration.RegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin event-management slice, proven end to end against real infrastructure — Postgres and a
 * real SMTP server (Mailpit), both Testcontainers — through the whole security filter chain
 * (issues #57 and #58):
 *
 * <ul>
 *   <li>a configured {@code ADMIN_EMAILS} account carries {@code ROLE_ADMIN} + {@code ROLE_USER} on
 *       authentication, and a regular account carries only {@code ROLE_USER};</li>
 *   <li>an admin creates, edits, deletes and cancels runs, and the public {@code GET /api/events}
 *       reflects each change;</li>
 *   <li><strong>delete is the empty-only escape hatch</strong> and <strong>cancel is the path for a
 *       run people signed up for</strong> (ADR-0001): delete is refused with a 409 the moment a
 *       registration exists, and cancelling emails every registrant, deduped, without rolling the
 *       cancellation back;</li>
 *   <li>registration counts reach the admin list and never the public payload;</li>
 *   <li>a non-admin is refused (403) and an anonymous caller is refused (401) on every one of these
 *       — the real boundary is server-side, not the hidden UI;</li>
 *   <li>an invalid submission (blank name, past start) is a 400 and nothing changes.</li>
 * </ul>
 *
 * {@code app.admin.emails} is set for the test so {@code admin@example.com} is the admin.
 */
@Import(TestcontainersConfiguration.class)
@Testcontainers
@SpringBootTest(properties = "app.admin.emails=admin@example.com")
@AutoConfigureMockMvc
class AdminEventIntegrationTest {

	private static final String ADMIN_EMAIL = "admin@example.com";
	private static final String USER_EMAIL = "ana@example.com";

	@Container
	static final GenericContainer<?> mailpit =
			new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.30.7"))
					.withExposedPorts(1025, 8025)
					.withEnv("MP_SMTP_AUTH_ACCEPT_ANY", "1")
					.withEnv("MP_SMTP_AUTH_ALLOW_INSECURE", "1")
					.waitingFor(Wait.forHttp("/readyz").forPort(8025));

	@DynamicPropertySource
	static void mailProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", mailpit::getHost);
		registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
	}

	private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newHttpClient();

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
	void reset() throws Exception {
		// Children first (FK to app_user), then the accounts — so each test signs up fresh emails
		// without tripping the unique-email rule against a prior test's rows. Cleared via JdbcTemplate
		// so this event-package test needs no cross-package handle on the auth repository.
		registrations.deleteAll();
		jdbc.update("delete from user_roles");
		jdbc.update("delete from app_user");
		HTTP.send(HttpRequest.newBuilder(mailpitUri("/api/v1/messages")).DELETE().build(),
				HttpResponse.BodyHandlers.discarding());
	}

	// --- Roles (#57) ---------------------------------------------------------------------------

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

	// --- Create (#57) --------------------------------------------------------------------------

	@Test
	void adminCreatesAnEventAndItAppearsOnTheEventsList() throws Exception {
		MockHttpSession admin = adminSession();

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
	void aBlankNameIsRejectedAndNothingIsCreated() throws Exception {
		MockHttpSession admin = adminSession();
		long before = events.count();

		mockMvc.perform(createEvent(admin, "   ", futureLocal()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(events.count()).isEqualTo(before);
	}

	@Test
	void aPastStartIsRejectedAndNothingIsCreated() throws Exception {
		MockHttpSession admin = adminSession();
		long before = events.count();

		String pastLocal = LocalDateTime.now().minusDays(1).withSecond(0).withNano(0).toString();

		mockMvc.perform(createEvent(admin, "Yesterday's run", pastLocal))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.startDateTime").exists());

		assertThat(events.count()).isEqualTo(before);
	}

	// --- Edit (#58) ----------------------------------------------------------------------------

	@Test
	void adminEditsAnEventAndTheChangeShowsOnThePublicList() throws Exception {
		MockHttpSession admin = adminSession();
		long id = createdEventId(admin, "Provisional 5k", futureLocal());
		String newStart = LocalDateTime.now().plusDays(45).withHour(8).withMinute(15)
				.withSecond(0).withNano(0).toString();

		mockMvc.perform(updateEvent(admin, id, "Renamed 5k", newStart))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.name").value("Renamed 5k"))
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.registrationCount").value(0));

		mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Renamed 5k')]").exists())
				.andExpect(jsonPath("$[?(@.name == 'Provisional 5k')]").doesNotExist());
	}

	@Test
	void editingAnEventIntoThePastIsRejectedAndNothingChanges() throws Exception {
		MockHttpSession admin = adminSession();
		long id = createdEventId(admin, "Stays as it is", futureLocal());
		String pastLocal = LocalDateTime.now().minusDays(1).withSecond(0).withNano(0).toString();

		mockMvc.perform(updateEvent(admin, id, "Backdated", pastLocal))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.startDateTime").exists());

		assertThat(events.findById(id).orElseThrow().getName()).isEqualTo("Stays as it is");
	}

	@Test
	void editingAnUnknownEventIsNotFound() throws Exception {
		mockMvc.perform(updateEvent(adminSession(), 9_999_999L, "Ghost run", futureLocal()))
				.andExpect(status().isNotFound());
	}

	// --- Delete: the empty-only escape hatch (#58, ADR-0001) -----------------------------------

	@Test
	void adminHardDeletesAnEventNobodyHasRegisteredFor() throws Exception {
		MockHttpSession admin = adminSession();
		long id = createdEventId(admin, "Created by mistake", futureLocal());

		mockMvc.perform(delete("/api/admin/events/" + id).session(admin))
				.andExpect(status().isNoContent());

		assertThat(events.findById(id)).isEmpty();
		mockMvc.perform(get("/api/events"))
				.andExpect(jsonPath("$[?(@.name == 'Created by mistake')]").doesNotExist());
	}

	@Test
	void deletingAnEventPeopleRegisteredForIsRefusedAndKeepsTheirData() throws Exception {
		MockHttpSession admin = adminSession();
		long id = createdEventId(admin, "Popular 5k", futureLocal());
		register(id, "Ana Pop", "ana.pop@example.com");

		mockMvc.perform(delete("/api/admin/events/" + id).session(admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errors.event").exists());

		// Neither the run nor the personal data attached to it was touched.
		assertThat(events.findById(id)).isPresent();
		assertThat(registrations.countByEventId(id)).isEqualTo(1);
	}

	@Test
	void deletingAnUnknownEventIsNotFound() throws Exception {
		mockMvc.perform(delete("/api/admin/events/9999999").session(adminSession()))
				.andExpect(status().isNotFound());
	}

	// --- Cancel: the path for a run people signed up for (#58, ADR-0001) -----------------------

	@Test
	void cancellingDropsTheRunFromThePublicListAndEmailsEveryRegistrantOnce() throws Exception {
		MockHttpSession admin = adminSession();
		long id = createdEventId(admin, "Rained off 5k", futureLocal());
		register(id, "Ana Pop", "ana.pop@example.com");
		register(id, "Radu Ion", "radu.ion@example.com");
		// A second row for Ana under a differently-cased address — the notifier must dedupe by email,
		// so she is told once, not twice. (Inserted directly: the API's own duplicate check blocks it.)
		jdbc.update("insert into registration (event_id, name, email) values (?, ?, ?)",
				id, "Ana Pop", "ANA.POP@example.com");
		clearMailbox();

		mockMvc.perform(post("/api/admin/events/" + id + "/cancel").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.registrationCount").value(3));

		// Off the public homepage the moment it is called off...
		mockMvc.perform(get("/api/events"))
				.andExpect(jsonPath("$[?(@.name == 'Rained off 5k')]").doesNotExist());
		// ...but still on the admin's schedule, badged, so it doesn't just vanish.
		mockMvc.perform(get("/api/admin/events").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Rained off 5k')].status").value("CANCELLED"));

		// Nobody's registration was destroyed — that is the whole point of cancelling (ADR-0001).
		assertThat(registrations.countByEventId(id)).isEqualTo(3);

		// Two people, two emails: the duplicate address was deduped.
		List<Map<String, Object>> messages = mailpitMessages();
		assertThat(messages).hasSize(2);
		assertThat(messages).extracting(AdminEventIntegrationTest::recipient)
				.containsExactlyInAnyOrder("ana.pop@example.com", "radu.ion@example.com");
		assertThat(messages).allSatisfy(message ->
				assertThat((String) message.get("Subject")).contains("Rained off 5k"));
	}

	@Test
	void aCancelledRunStaysOnItsRegistrantsDashboardAndCannotBeRegisteredForAgain() throws Exception {
		MockHttpSession admin = adminSession();
		long id = createdEventId(admin, "Cancelled 5k", futureLocal());
		MockHttpSession runner = sessionOf(mockMvc.perform(signup(USER_EMAIL, "Ana Pop"))
				.andExpect(status().isCreated()).andReturn());
		mockMvc.perform(post("/api/registrations").session(runner).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"eventId": %d, "name": "Ana Pop", "email": "%s"}""".formatted(id, USER_EMAIL)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/admin/events/" + id + "/cancel").session(admin))
				.andExpect(status().isOk());

		// Her dashboard still lists it, flagged as cancelled, rather than silently losing the run.
		mockMvc.perform(get("/api/me/registrations").session(runner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upcoming[?(@.eventName == 'Cancelled 5k')].cancelled").value(true));

		// And a stale page can't sign anyone else up for it.
		mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"eventId": %d, "name": "Radu Ion", "email": "radu.ion@example.com"}""".formatted(id)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.eventId").exists());
	}

	@Test
	void aCancelledEventIsReadOnly() throws Exception {
		MockHttpSession admin = adminSession();
		long id = createdEventId(admin, "Already off", futureLocal());
		mockMvc.perform(post("/api/admin/events/" + id + "/cancel").session(admin))
				.andExpect(status().isOk());

		// Cancellation is terminal: no edit, and no second cancel.
		mockMvc.perform(updateEvent(admin, id, "Back on", futureLocal()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errors.event").exists());
		mockMvc.perform(post("/api/admin/events/" + id + "/cancel").session(admin))
				.andExpect(status().isConflict());

		assertThat(events.findById(id).orElseThrow().getName()).isEqualTo("Already off");
	}

	// --- Registration counts (#58) -------------------------------------------------------------

	@Test
	void theAdminListCarriesLiveRegistrationCountsAndThePublicListCarriesNone() throws Exception {
		MockHttpSession admin = adminSession();
		long id = createdEventId(admin, "Counted 5k", futureLocal());
		register(id, "Ana Pop", "ana.pop@example.com");
		register(id, "Radu Ion", "radu.ion@example.com");

		mockMvc.perform(get("/api/admin/events").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Counted 5k')].registrationCount").value(2));

		mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Counted 5k')]").exists())
				.andExpect(jsonPath("$[0].registrationCount").doesNotExist());
	}

	// --- The authorisation boundary (#57, #58) -------------------------------------------------

	@Test
	void nonAdminIsForbiddenFromCreating() throws Exception {
		MockHttpSession user = userSession();
		long before = events.count();

		mockMvc.perform(createEvent(user, "Sneaky run", futureLocal()))
				.andExpect(status().isForbidden());

		assertThat(events.count()).isEqualTo(before);
	}

	@Test
	void nonAdminIsForbiddenFromEditingDeletingCancellingAndReadingCounts() throws Exception {
		long id = createdEventId(adminSession(), "Not yours", futureLocal());
		MockHttpSession user = userSession();

		mockMvc.perform(get("/api/admin/events").session(user)).andExpect(status().isForbidden());
		mockMvc.perform(updateEvent(user, id, "Hijacked", futureLocal())).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/events/" + id).session(user)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/events/" + id + "/cancel").session(user))
				.andExpect(status().isForbidden());

		Event untouched = events.findById(id).orElseThrow();
		assertThat(untouched.getName()).isEqualTo("Not yours");
		assertThat(untouched.getStatus()).isEqualTo(EventStatus.SCHEDULED);
	}

	@Test
	void anonymousIsUnauthorisedForEveryAdminEventCall() throws Exception {
		// Anonymous hits the deny-by-default 401 (this API has no login page to redirect to); an
		// authenticated non-admin gets 403. Either way nothing changes.
		long id = createdEventId(adminSession(), "Untouchable", futureLocal());

		mockMvc.perform(get("/api/admin/events")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Sneaky run", "startDateTime": "%s"}""".formatted(futureLocal())))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/admin/events/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Hijacked", "startDateTime": "%s"}""".formatted(futureLocal())))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/admin/events/" + id)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/events/" + id + "/cancel")).andExpect(status().isUnauthorized());

		Event untouched = events.findById(id).orElseThrow();
		assertThat(untouched.getName()).isEqualTo("Untouchable");
		assertThat(untouched.getStatus()).isEqualTo(EventStatus.SCHEDULED);
	}

	// --- Fixtures ------------------------------------------------------------------------------

	private MockHttpSession adminSession() throws Exception {
		return sessionOf(mockMvc.perform(signup(ADMIN_EMAIL, "Boss"))
				.andExpect(status().isCreated()).andReturn());
	}

	private MockHttpSession userSession() throws Exception {
		return sessionOf(mockMvc.perform(signup(USER_EMAIL, "Ana Pop"))
				.andExpect(status().isCreated()).andReturn());
	}

	/** Create a run as the admin and hand back the id the DB gave it. */
	private long createdEventId(MockHttpSession admin, String name, String startDateTime) throws Exception {
		MvcResult result = mockMvc.perform(createEvent(admin, name, startDateTime))
				.andExpect(status().isCreated()).andReturn();
		Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(),
				new TypeReference<>() {
				});
		return ((Number) body.get("id")).longValue();
	}

	/** An anonymous registration — the core loop, used here only to give an event registrants. */
	private void register(long eventId, String name, String email) throws Exception {
		mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"eventId": %d, "name": "%s", "email": "%s"}""".formatted(eventId, name, email)))
				.andExpect(status().isCreated());
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

	private static MockHttpServletRequestBuilder updateEvent(MockHttpSession session, long id, String name,
			String startDateTime) {
		return put("/api/admin/events/" + id).session(session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "startDateTime": "%s"}""".formatted(name, startDateTime));
	}

	private static MockHttpSession sessionOf(MvcResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	// --- Mailpit ------------------------------------------------------------------------------

	private void clearMailbox() throws Exception {
		HTTP.send(HttpRequest.newBuilder(mailpitUri("/api/v1/messages")).DELETE().build(),
				HttpResponse.BodyHandlers.discarding());
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> mailpitMessages() throws Exception {
		HttpResponse<String> response = HTTP.send(
				HttpRequest.newBuilder(mailpitUri("/api/v1/messages")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
		});
		return (List<Map<String, Object>>) payload.get("messages");
	}

	@SuppressWarnings("unchecked")
	private static String recipient(Map<String, Object> message) {
		List<Map<String, Object>> to = (List<Map<String, Object>>) message.get("To");
		return (String) to.get(0).get("Address");
	}

	private static URI mailpitUri(String path) {
		return URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
	}

}
