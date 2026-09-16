package ro.threet.run.weather;

/**
 * A small, provider-neutral set of sky/precipitation categories. Upstream weather codes (Open-Meteo
 * uses WMO codes) collapse into these so the UI picks from a handful of icons rather than dozens of
 * raw codes. {@link #UNKNOWN} is the safe fallback for a code we don't recognise.
 */
public enum WeatherCondition {
	CLEAR,
	PARTLY_CLOUDY,
	CLOUDY,
	FOG,
	DRIZZLE,
	RAIN,
	SNOW,
	THUNDERSTORM,
	UNKNOWN
}
