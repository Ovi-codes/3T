package ro.threet.run.weather;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@link WeatherProvider} backed by <a href="https://open-meteo.com">Open-Meteo</a>: free, no API
 * key, EU-hosted
 *
 * <p>The base URL is injected (env-driven, see {@link WeatherConfig}); the caching
 * lives one layer out in {@link CachingWeatherProvider}, so this class is a pure upstream call.
 */
class OpenMeteoWeatherProvider implements WeatherProvider {

	private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherProvider.class);

	/** Open-Meteo's free forecast reaches ~16 days out; beyond that there is no data to ask for. */
	private final int horizonDays;
	private final RestClient restClient;
	private final Clock clock;

	OpenMeteoWeatherProvider(RestClient restClient, Clock clock, int horizonDays) {
		this.restClient = restClient;
		this.clock = clock;
		this.horizonDays = horizonDays;
	}

	@Override
	public Optional<Forecast> forecast(double latitude, double longitude, LocalDate date) {
		LocalDate today = LocalDate.now(clock);
		if (date.isBefore(today) || date.isAfter(today.plusDays(horizonDays))) {
			// Nothing to fetch: a past day, or beyond the forecast horizon.
			return Optional.empty();
		}

		try {
			OpenMeteoResponse response = restClient.get()
					.uri(uri -> uri
							.queryParam("latitude", latitude)
							.queryParam("longitude", longitude)
							.queryParam("daily",
									"weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
							.queryParam("timezone", "auto")
							.queryParam("start_date", date)
							.queryParam("end_date", date)
							.build())
					.retrieve()
					.body(OpenMeteoResponse.class);

			return toForecast(response, date);
		} catch (RestClientException e) {
			// Upstream unreachable or a non-2xx — degrade quietly, the caller renders without a forecast.
			log.warn("Open-Meteo forecast lookup failed for {},{} on {}: {}", latitude, longitude, date,
					e.getMessage());
			return Optional.empty();
		}
	}

	private Optional<Forecast> toForecast(OpenMeteoResponse response, LocalDate date) {
		OpenMeteoResponse.Daily daily = response == null ? null : response.daily();
		if (daily == null || daily.time() == null || daily.time().isEmpty()) {
			return Optional.empty();
		}

		// We asked for a single day, so index 0 is that day.
		WmoWeatherCode.Reading reading = WmoWeatherCode.interpret(first(daily.weatherCode(), 0));
		return Optional.of(new Forecast(
				date,
				reading.condition(),
				reading.description(),
				first(daily.temperatureMax(), Double.NaN),
				first(daily.temperatureMin(), Double.NaN),
				first(daily.precipitationProbabilityMax(), 0)));
	}

	private static int first(List<Integer> values, int fallback) {
		Integer v = values == null || values.isEmpty() ? null : values.get(0);
		return v == null ? fallback : v;
	}

	private static double first(List<Double> values, double fallback) {
		Double v = values == null || values.isEmpty() ? null : values.get(0);
		return v == null ? fallback : v;
	}

	/** The slice of Open-Meteo's response we read: the {@code daily} block's parallel arrays. */
	record OpenMeteoResponse(Daily daily) {

		record Daily(
				List<String> time,
				@JsonProperty("weather_code") List<Integer> weatherCode,
				@JsonProperty("temperature_2m_max") List<Double> temperatureMax,
				@JsonProperty("temperature_2m_min") List<Double> temperatureMin,
				@JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax) {
		}
	}

}
