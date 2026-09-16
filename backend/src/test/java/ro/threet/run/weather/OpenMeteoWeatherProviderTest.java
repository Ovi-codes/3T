package ro.threet.run.weather;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

	private RestClient.Builder builder;
	private MockRestServiceServer server;
	private OpenMeteoWeatherProvider provider;

	@BeforeEach
	void setUp() {
		builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		provider = new OpenMeteoWeatherProvider(builder.baseUrl("https://weather.test/forecast").build(), FIXED, 16);
	}

	@Test
	void parsesTheDailyForecastForAnInHorizonDate() {
		String body = """
				{
				  "daily": {
				    "time": ["2026-09-18"],
				    "weather_code": [61],
				    "temperature_2m_max": [22.4],
				    "temperature_2m_min": [11.9],
				    "precipitation_probability_max": [55]
				  }
				}
				""";
		server.expect(method(GET))
				.andExpect(queryParam("latitude", "44.41"))
				.andExpect(queryParam("longitude", "26.1"))
				.andExpect(queryParam("start_date", "2026-09-18"))
				.andExpect(queryParam("end_date", "2026-09-18"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		Optional<Forecast> forecast = provider.forecast(LAT, LON, LocalDate.parse("2026-09-18"));

		server.verify();
		assertThat(forecast).hasValueSatisfying(f -> {
			assertThat(f.date()).isEqualTo(LocalDate.parse("2026-09-18"));
			assertThat(f.condition()).isEqualTo(WeatherCondition.RAIN);
			assertThat(f.description()).isEqualTo("Light rain");
			assertThat(f.temperatureMaxC()).isEqualTo(22.4);
			assertThat(f.temperatureMinC()).isEqualTo(11.9);
			assertThat(f.precipitationProbabilityMax()).isEqualTo(55);
		});
	}

	@Test
	void returnsEmptyWithoutCallingUpstreamWhenDateIsBeyondHorizon() {
		// No server expectation set: if any request were made, verify() would fail.
		Optional<Forecast> forecast = provider.forecast(LAT, LON, LocalDate.parse("2026-10-30"));

		server.verify();
		assertThat(forecast).isEmpty();
	}

	@Test
	void returnsEmptyWithoutCallingUpstreamForAPastDate() {
		Optional<Forecast> forecast = provider.forecast(LAT, LON, LocalDate.parse("2026-09-14"));

		server.verify();
		assertThat(forecast).isEmpty();
	}

	@Test
	void degradesToEmptyWhenUpstreamFails() {
		server.expect(method(GET)).andRespond(withServerError());

		Optional<Forecast> forecast = provider.forecast(LAT, LON, LocalDate.parse("2026-09-18"));

		server.verify();
		assertThat(forecast).isEmpty();
	}

}
