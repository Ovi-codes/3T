package ro.threet.run.registration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.event.EventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CS-1 proven at the backend boundary against real infrastructure: Postgres (Testcontainers) and a
 * real SMTP server (Mailpit, also a container). A valid submit persists exactly one row and puts
 * exactly one message in Mailpit addressed to the registrant; an invalid email is a 400 that
 * touches neither. Mailpit is queried over its HTTP API, the same way the E2E test will.
 */
@Import(TestcontainersConfiguration.class)
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationControllerIntegrationTest {

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

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RegistrationRepository registrationRepository;

	@Autowired
	private EventRepository eventRepository;

	@BeforeEach
	void reset() throws Exception {
		registrationRepository.deleteAll();
		HTTP.send(HttpRequest.newBuilder(mailpitUri("/api/v1/messages")).DELETE().build(),
				HttpResponse.BodyHandlers.discarding());
	}

	@Test
	void validRegistrationPersistsRowAndSendsExactlyOneEmail() throws Exception {
		long eventId = firstUpcomingEventId();
		String body = """
				{"eventId": %d, "name": "Ana Pop", "email": "ana.pop@example.com"}""".formatted(eventId);

		mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventId").value(eventId))
				.andExpect(jsonPath("$.email").value("ana.pop@example.com"));

		assertThat(registrationRepository.count()).isEqualTo(1);

		List<Map<String, Object>> messages = mailpitMessages();
		assertThat(messages).hasSize(1);
		assertThat(recipient(messages.get(0))).isEqualTo("ana.pop@example.com");
	}

	@Test
	void invalidEmailIsRejectedAndNothingHappens() throws Exception {
		long eventId = firstUpcomingEventId();
		String body = """
				{"eventId": %d, "name": "Ana Pop", "email": "not-an-email"}""".formatted(eventId);

		mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.email").exists());

		assertThat(registrationRepository.count()).isZero();
		assertThat(mailpitMessages()).isEmpty();
	}

	private long firstUpcomingEventId() {
		return eventRepository
				.findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(OffsetDateTime.now())
				.get(0)
				.getId();
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