package ro.threet.run.event;

import java.time.OffsetDateTime;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import ro.threet.run.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code GET /api/events} against a real Postgres seeded by the V2 migration: the
 * two past events are excluded and the four upcoming ones come back soonest-first.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void returnsSeededUpcomingEventsInDateOrderAndExcludesPast() throws Exception {
		String json = mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		List<EventResponse> events = objectMapper.readValue(json, new TypeReference<>() {
		});

		OffsetDateTime now = OffsetDateTime.now();
		// V2 seeds 6 events for Bucharest: 2 in the past (excluded), 4 upcoming.
		assertThat(events).hasSize(4);
		assertThat(events).allSatisfy(event -> {
			assertThat(event.startDateTime()).isAfter(now);
			assertThat(event.locationName()).isEqualTo("Tineretului Park");
			assertThat(event.city()).isEqualTo("Bucharest");
		});
		assertThat(events).extracting(EventResponse::startDateTime).isSorted();
	}

}