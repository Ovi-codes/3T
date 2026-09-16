package ro.threet.run.weather;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WmoWeatherCodeTest {

	@Test
	void mapsKnownCodesToConditionAndLabel() {
		assertThat(WmoWeatherCode.interpret(0))
				.isEqualTo(new WmoWeatherCode.Reading(WeatherCondition.CLEAR, "Clear sky"));
		assertThat(WmoWeatherCode.interpret(3))
				.isEqualTo(new WmoWeatherCode.Reading(WeatherCondition.CLOUDY, "Overcast"));
		assertThat(WmoWeatherCode.interpret(61))
				.isEqualTo(new WmoWeatherCode.Reading(WeatherCondition.RAIN, "Light rain"));
		assertThat(WmoWeatherCode.interpret(95))
				.isEqualTo(new WmoWeatherCode.Reading(WeatherCondition.THUNDERSTORM, "Thunderstorm"));
		assertThat(WmoWeatherCode.interpret(73))
				.isEqualTo(new WmoWeatherCode.Reading(WeatherCondition.SNOW, "Snow"));
		assertThat(WmoWeatherCode.interpret(53))
				.isEqualTo(new WmoWeatherCode.Reading(WeatherCondition.DRIZZLE, "Drizzle"));
	}

	@Test
	void unknownCodeFallsBackRatherThanThrowing() {
		assertThat(WmoWeatherCode.interpret(-1).condition()).isEqualTo(WeatherCondition.UNKNOWN);
		assertThat(WmoWeatherCode.interpret(1234).condition()).isEqualTo(WeatherCondition.UNKNOWN);
	}

}
