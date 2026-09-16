package ro.threet.run.weather;

/**
 * Maps a WMO weather-interpretation code (WW) — what Open-Meteo returns in {@code weather_code} —
 * to a {@link WeatherCondition} and a short human label. One switch keeps the category and the
 * label in sync. Unknown codes fall back to {@link WeatherCondition#UNKNOWN} rather than throwing,
 * so a new upstream code never breaks a forecast.
 *
 * @see <a href="https://open-meteo.com/en/docs">Open-Meteo docs — WMO weather codes</a>
 */
final class WmoWeatherCode {

	/** A code's category plus its display label. */
	record Reading(WeatherCondition condition, String description) {
	}

	private WmoWeatherCode() {
	}

	static Reading interpret(int code) {
		return switch (code) {
			case 0 -> new Reading(WeatherCondition.CLEAR, "Clear sky");
			case 1 -> new Reading(WeatherCondition.PARTLY_CLOUDY, "Mainly clear");
			case 2 -> new Reading(WeatherCondition.PARTLY_CLOUDY, "Partly cloudy");
			case 3 -> new Reading(WeatherCondition.CLOUDY, "Overcast");
			case 45 -> new Reading(WeatherCondition.FOG, "Fog");
			case 48 -> new Reading(WeatherCondition.FOG, "Rime fog");
			case 51 -> new Reading(WeatherCondition.DRIZZLE, "Light drizzle");
			case 53 -> new Reading(WeatherCondition.DRIZZLE, "Drizzle");
			case 55 -> new Reading(WeatherCondition.DRIZZLE, "Dense drizzle");
			case 56, 57 -> new Reading(WeatherCondition.DRIZZLE, "Freezing drizzle");
			case 61 -> new Reading(WeatherCondition.RAIN, "Light rain");
			case 63 -> new Reading(WeatherCondition.RAIN, "Rain");
			case 65 -> new Reading(WeatherCondition.RAIN, "Heavy rain");
			case 66, 67 -> new Reading(WeatherCondition.RAIN, "Freezing rain");
			case 71 -> new Reading(WeatherCondition.SNOW, "Light snow");
			case 73 -> new Reading(WeatherCondition.SNOW, "Snow");
			case 75 -> new Reading(WeatherCondition.SNOW, "Heavy snow");
			case 77 -> new Reading(WeatherCondition.SNOW, "Snow grains");
			case 80 -> new Reading(WeatherCondition.RAIN, "Light showers");
			case 81 -> new Reading(WeatherCondition.RAIN, "Showers");
			case 82 -> new Reading(WeatherCondition.RAIN, "Heavy showers");
			case 85 -> new Reading(WeatherCondition.SNOW, "Snow showers");
			case 86 -> new Reading(WeatherCondition.SNOW, "Heavy snow showers");
			case 95 -> new Reading(WeatherCondition.THUNDERSTORM, "Thunderstorm");
			case 96, 99 -> new Reading(WeatherCondition.THUNDERSTORM, "Thunderstorm with hail");
			default -> new Reading(WeatherCondition.UNKNOWN, "Unknown");
		};
	}

}
