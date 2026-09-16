package ro.threet.run.weather;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A forecast for a point on a day
 * A different provider is a new implementation + a wiring change in
 * {@link WeatherConfig}
 */
public interface WeatherProvider {

	/**
	 * The forecast for {@code date} at the given coordinates, or {@link Optional#empty()} when none is
	 * available — the date is outside the provider's forecast horizon, or the upstream call failed.
	 * Never throws for an unavailable forecast: the caller degrades gracefully
	 */
	Optional<Forecast> forecast(double latitude, double longitude, LocalDate date);

}
