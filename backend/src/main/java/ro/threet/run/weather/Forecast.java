package ro.threet.run.weather;

import java.time.LocalDate;

/**
 * A day's forecast for one location, provider-neutral: the fields the UI needs, already mapped off
 * whatever the upstream API returned (WMO codes become a {@link WeatherCondition} in the provider).
 *
 * @param date            the forecast day
 * @param condition       the sky/precipitation category, for choosing an icon
 * @param description     a short human label for {@code condition} (e.g. "Light rain")
 * @param temperatureMaxC daytime high, °C
 * @param temperatureMinC overnight low, °C
 * @param precipitationProbabilityMax highest chance of precipitation across the day, percent (0–100)
 */
public record Forecast(
		LocalDate date,
		WeatherCondition condition,
		String description,
		double temperatureMaxC,
		double temperatureMinC,
		int precipitationProbabilityMax) {
}
