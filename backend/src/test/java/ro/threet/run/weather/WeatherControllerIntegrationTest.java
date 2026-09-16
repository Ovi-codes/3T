package ro.threet.run.weather;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.event.EventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/events/{id}/forecast} against a real Postgres (V6 gives Bucharest coordinates),
 * with the upstream {@link WeatherProvider} mocked — no outbound HTTP in tests (charter §5 DoD).
 * Covers the three shapes: a forecast, a graceful "unavailable", and an unknown event.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WeatherControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EventRepository eventRepository;

	@MockitoBean
	private WeatherProvider weatherProvider;

	private long anEventId() {
		return eventRepository.findAll().getFirst().getId();
	}

	@Test
	void returnsTheForecastWhenOneIsAvailable() throws Exception {
		Forecast forecast = new Forecast(
				LocalDate.parse("2026-09-18"), WeatherCondition.RAIN, "Light rain", 22.4, 11.9, 55);
		when(weatherProvider.forecast(anyDouble(), anyDouble(), any())).thenReturn(Optional.of(forecast));

		mockMvc.perform(get("/api/events/{id}/forecast", anEventId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(true))
				.andExpect(jsonPath("$.condition").value("rain"))
				.andExpect(jsonPath("$.description").value("Light rain"))
				.andExpect(jsonPath("$.temperatureMaxC").value(22.4))
				.andExpect(jsonPath("$.temperatureMinC").value(11.9))
				.andExpect(jsonPath("$.precipitationProbabilityMax").value(55));
	}

	@Test
	void degradesGracefullyWhenNoForecastIsAvailable() throws Exception {
		when(weatherProvider.forecast(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/events/{id}/forecast", anEventId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(false))
				.andExpect(jsonPath("$.condition").doesNotExist())
				.andExpect(jsonPath("$.temperatureMaxC").doesNotExist());
	}

	@Test
	void returns404ForAnUnknownEvent() throws Exception {
		mockMvc.perform(get("/api/events/{id}/forecast", 999_999))
				.andExpect(status().isNotFound());
	}

	@Test
	void sendsTheEventsCoordinatesAndDateUpstreamAndNothingElse() throws Exception {
		// Guards the GDPR promise: only the location's coordinates + the event date cross the seam.
		when(weatherProvider.forecast(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/events/{id}/forecast", anEventId())).andExpect(status().isOk());

		var latitude = org.mockito.ArgumentCaptor.forClass(Double.class);
		var longitude = org.mockito.ArgumentCaptor.forClass(Double.class);
		var date = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
		org.mockito.Mockito.verify(weatherProvider).forecast(latitude.capture(), longitude.capture(), date.capture());
		assertThat(latitude.getValue()).isEqualTo(44.4085);
		assertThat(longitude.getValue()).isEqualTo(26.1039);
		assertThat(date.getValue()).isNotNull();
	}

}
