package ro.threet.run.weather;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Body of {@code GET /api/events/{id}/forecast}. The {@code available} flag is the whole contract
 * the UI needs: {@code false} (with every other field null) means "no forecast to show" — the date
 * is beyond the horizon, the location has no coordinates, or the upstream API was unavailable — and
 * the page simply omits the widget. {@code true} carries the numbers for the event's time.
 *
 * <p>{@code condition} is the lowercase {@link WeatherCondition} name (e.g. {@code "rain"}) so the
 * UI can map it to an icon; {@code description} is the ready-to-show label. {@code temperatureC} is
 * a whole degree, or null when the provider had no value for the hour.
 */
public record ForecastResponse(
		boolean available,
		LocalDate date,
		String condition,
		String description,
		Integer temperatureC,
		Integer precipitationProbability) {

	static ForecastResponse unavailable() {
		return new ForecastResponse(false, null, null, null, null, null);
	}

	static ForecastResponse of(Forecast forecast) {
		return new ForecastResponse(
				true,
				forecast.date(),
				forecast.condition().name().toLowerCase(Locale.ROOT),
				forecast.description(),
				forecast.temperatureC(),
				forecast.precipitationProbability());
	}

}
