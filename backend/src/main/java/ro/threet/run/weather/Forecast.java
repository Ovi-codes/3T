package ro.threet.run.weather;

import java.time.LocalDate;

/**
 * A forecast for one location at the event's hour, provider-neutral: the fields the UI needs, already
 * mapped off whatever the upstream API returned (WMO codes become a {@link WeatherCondition} in the
 * provider, the temperature rounded to whole degrees).
 *
 * @param date                     the forecast day
 * @param condition                the sky/precipitation category, for choosing an icon
 * @param description              a short human label for {@code condition} (e.g. "Light rain")
 * @param temperatureC             temperature at the event hour, whole °C; null when upstream had no value
 * @param precipitationProbability chance of precipitation at the event hour, percent (0–100)
 */
public record Forecast(
		LocalDate date,
		WeatherCondition condition,
		String description,
		Integer temperatureC,
		int precipitationProbability) {
}
