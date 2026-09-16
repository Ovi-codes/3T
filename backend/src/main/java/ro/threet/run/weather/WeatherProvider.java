package ro.threet.run.weather;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A forecast for a point at a moment (the event's local date and hour)
 * A different provider is a new implementation + a wiring change in
 * {@link WeatherConfig}
 */
public interface WeatherProvider {

	/**
	 * The forecast for {@code dateTime} at the given coordinates, or {@link Optional#empty()} when none
	 * is available — the date is outside the provider's forecast horizon, or the upstream call failed.
	 * The hour of {@code dateTime} (in the location's local zone) selects which hourly reading to
	 * return. Never throws for an unavailable forecast: the caller degrades gracefully
	 */
	Optional<Forecast> forecast(double latitude, double longitude, LocalDateTime dateTime);

}
