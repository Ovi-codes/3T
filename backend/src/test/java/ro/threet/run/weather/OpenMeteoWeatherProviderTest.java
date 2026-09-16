package ro.threet.run.weather;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * The Open-Meteo provider in isolation: the upstream is a {@link MockRestServiceServer} so no real
 * network call happens. "Today" is a fixed clock so the horizon rule is deterministic.
 */
class OpenMeteoWeatherProviderTest {

	// Fixed "now": 2026-09-15. The Bucharest run three days out (18th) is well inside the horizon.
	private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-15T09:00:00Z"), ZoneOffset.UTC);
	private static final double LAT = 44.41;
	private static final double LON = 26.1;
	private static final LocalDateTime DATE_TIME = LocalDateTime.parse("2026-09-18T09:00");

	private RestClient.Builder builder;
	private MockRestServiceServer server;
	private OpenMeteoWeatherProvider provider;

	@BeforeEach
	void setUp() {
		builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		provider = new OpenMeteoWeatherProvider(builder.baseUrl("https://weather.test/forecast").build(), FIXED, 8);
	}

	@Test
	void parsesTheForecastAtTheEventHour() {
		// Three hourly slots; the 09:00 one is the run's hour and the only reading that should surface.
		String body = """
				{
				  "hourly": {
				    "time": ["2026-09-18T08:00", "2026-09-18T09:00", "2026-09-18T10:00"],
				    "weather_code": [3, 61, 80],
				    "temperature_2m": [12.1, 15.6, 18.2],
				    "precipitation_probability": [20, 55, 70]
				  }
				}
				""";
		server.expect(method(GET))
				.andExpect(queryParam("latitude", "44.41"))
				.andExpect(queryParam("longitude", "26.1"))
				.andExpect(queryParam("start_date", "2026-09-18"))
				.andExpect(queryParam("end_date", "2026-09-18"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		Optional<Forecast> forecast = provider.forecast(LAT, LON, DATE_TIME);

		server.verify();
		assertThat(forecast).hasValueSatisfying(f -> {
			assertThat(f.date()).isEqualTo(LocalDate.parse("2026-09-18"));
			assertThat(f.condition()).isEqualTo(WeatherCondition.RAIN);
			assertThat(f.description()).isEqualTo("Light rain");
			assertThat(f.temperatureC()).isEqualTo(16); // 15.6 rounded
			assertThat(f.precipitationProbability()).isEqualTo(55);
		});
	}

	@Test
	void fallsBackToTheNearestFutureHourWhenTheEventHourIsAbsent() {
		// No 09:00 slot: the run reads 10:00 (the nearest hour still ahead), not the earlier 08:00.
		String body = """
				{
				  "hourly": {
				    "time": ["2026-09-18T08:00", "2026-09-18T10:00"],
				    "weather_code": [3, 80],
				    "temperature_2m": [12.1, 18.2],
				    "precipitation_probability": [20, 70]
				  }
				}
				""";
		server.expect(method(GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		Optional<Forecast> forecast = provider.forecast(LAT, LON, DATE_TIME);

		server.verify();
		assertThat(forecast).hasValueSatisfying(f -> {
			assertThat(f.condition()).isEqualTo(WeatherCondition.RAIN);
			assertThat(f.description()).isEqualTo("Light showers");
			assertThat(f.temperatureC()).isEqualTo(18); // 18.2 rounded, from the 10:00 slot
			assertThat(f.precipitationProbability()).isEqualTo(70);
		});
	}

	@Test
	void returnsEmptyWhenNoHourAtOrAfterTheEventHourIsAvailable() {
		// Only earlier slots came back — nothing ahead of the run to show, so degrade rather than
		// reach backwards to a stale past hour.
		String body = """
				{
				  "hourly": {
				    "time": ["2026-09-18T07:00", "2026-09-18T08:00"],
				    "weather_code": [3, 3],
				    "temperature_2m": [10.0, 12.1],
				    "precipitation_probability": [10, 20]
				  }
				}
				""";
		server.expect(method(GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		Optional<Forecast> forecast = provider.forecast(LAT, LON, DATE_TIME);

		server.verify();
		assertThat(forecast).isEmpty();
	}

	@Test
	void returnsEmptyWithoutCallingUpstreamWhenDateIsBeyondHorizon() {
		// No server expectation set: if any request were made, verify() would fail.
		Optional<Forecast> forecast = provider.forecast(LAT, LON, LocalDateTime.parse("2026-10-30T09:00"));

		server.verify();
		assertThat(forecast).isEmpty();
	}

	@Test
	void returnsEmptyWithoutCallingUpstreamForAPastDate() {
		Optional<Forecast> forecast = provider.forecast(LAT, LON, LocalDateTime.parse("2026-09-14T09:00"));

		server.verify();
		assertThat(forecast).isEmpty();
	}

	@Test
	void degradesToEmptyWhenUpstreamFails() {
		server.expect(method(GET)).andRespond(withServerError());

		Optional<Forecast> forecast = provider.forecast(LAT, LON, DATE_TIME);

		server.verify();
		assertThat(forecast).isEmpty();
	}

}
