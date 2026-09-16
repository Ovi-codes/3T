package ro.threet.run.weather;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
 * <p>Reads the <em>hourly</em> forecast and picks the reading at the event's hour, so the widget
 * shows one temperature for when the run starts.
 *
 * <p>The base URL is injected (env-driven, see {@link WeatherConfig}); the caching
 * lives one layer out in {@link CachingWeatherProvider}, so this class is a pure upstream call.
 */
class OpenMeteoWeatherProvider implements WeatherProvider {

	private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherProvider.class);

	/** Open-Meteo's hourly timestamps are local ISO to the minute, e.g. {@code 2026-09-18T09:00}. */
	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

	/** How many days ahead we ask for. */
	private final int horizonDays;
	private final RestClient restClient;
	private final Clock clock;

	OpenMeteoWeatherProvider(RestClient restClient, Clock clock, int horizonDays) {
		this.restClient = restClient;
		this.clock = clock;
		this.horizonDays = horizonDays;
	}

	@Override
	public Optional<Forecast> forecast(double latitude, double longitude, LocalDateTime dateTime) {
		LocalDate date = dateTime.toLocalDate();
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
							.queryParam("hourly", "weather_code,temperature_2m,precipitation_probability")
							.queryParam("timezone", "auto")
							.queryParam("start_date", date)
							.queryParam("end_date", date)
							.build())
					.retrieve()
					.body(OpenMeteoResponse.class);

			return toForecast(response, dateTime);
		} catch (RestClientException e) {
			// Upstream unreachable or a non-2xx — degrade quietly, the caller renders without a forecast.
			log.warn("Open-Meteo forecast lookup failed for {},{} at {}: {}", latitude, longitude, dateTime,
					e.getMessage());
			return Optional.empty();
		}
	}

	private Optional<Forecast> toForecast(OpenMeteoResponse response, LocalDateTime dateTime) {
		OpenMeteoResponse.Hourly hourly = response == null ? null : response.hourly();
		if (hourly == null || hourly.time() == null || hourly.time().isEmpty()) {
			return Optional.empty();
		}

		// The hourly arrays are parallel; take the event's hour, or the nearest one after it when the
		// response has no reading exactly then.
		int i = indexAtOrAfter(hourly.time(), nearestHour(dateTime));
		if (i < 0) {
			return Optional.empty();
		}

		WmoWeatherCode.Reading reading = WmoWeatherCode.interpret(at(hourly.weatherCode(), i, 0));
		return Optional.of(new Forecast(
				dateTime.toLocalDate(),
				reading.condition(),
				reading.description(),
				roundedOrNull(at(hourly.temperature(), i)),
				at(hourly.precipitationProbability(), i, 0)));
	}

	/** Snap to the nearest whole hour */
	private static LocalDateTime nearestHour(LocalDateTime dateTime) {
		LocalDateTime hour = dateTime.truncatedTo(ChronoUnit.HOURS);
		return dateTime.getMinute() >= 30 ? hour.plusHours(1) : hour;
	}

	/**
	 * The slot at {@code target}, or — when the response has no reading exactly then — the earliest
	 * slot after it (the nearest forecast still ahead of the run). {@code -1} when every slot is
	 * before the target, so there's nothing to show. Scans rather than assuming the array is sorted.
	 */
	private static int indexAtOrAfter(List<String> times, LocalDateTime target) {
		int best = -1;
		LocalDateTime bestTime = null;
		for (int i = 0; i < times.size(); i++) {
			LocalDateTime slot = LocalDateTime.parse(times.get(i), HOUR);
			if (!slot.isBefore(target) && (bestTime == null || slot.isBefore(bestTime))) {
				best = i;
				bestTime = slot;
			}
		}
		return best;
	}

	/** A temperature the provider couldn't fill (missing/null) stays null; otherwise a whole degree. */
	private static Integer roundedOrNull(Double value) {
		return value == null ? null : (int) Math.round(value);
	}

	private static Double at(List<Double> values, int index) {
		return values == null || index >= values.size() ? null : values.get(index);
	}

	private static int at(List<Integer> values, int index, int fallback) {
		Integer v = values == null || index >= values.size() ? null : values.get(index);
		return v == null ? fallback : v;
	}

	/** The slice of Open-Meteo's response we read: the {@code hourly} block's parallel arrays. */
	record OpenMeteoResponse(Hourly hourly) {

		record Hourly(
				List<String> time,
				@JsonProperty("weather_code") List<Integer> weatherCode,
				@JsonProperty("temperature_2m") List<Double> temperature,
				@JsonProperty("precipitation_probability") List<Integer> precipitationProbability) {
		}
	}

}
