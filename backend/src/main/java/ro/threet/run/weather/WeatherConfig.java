package ro.threet.run.weather;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the weather seam from the environment (charter Azure config rules — no hardcoded URLs).
 * The upstream base URL, forecast horizon and cache TTL are all env-driven; a different provider is
 * a new {@link WeatherProvider} built here, not edits across the app (charter §3).
 *
 * <p>Assembling the provider here (rather than component-scanning the implementations) keeps a
 * single {@link WeatherProvider} bean — the cached one — and lets the plain classes stay unit-tested
 * in isolation.
 */
@Configuration
class WeatherConfig {

	/** Open-Meteo's forecast endpoint. Overridable so tests point at a stub and prod can swap hosts. */
	@Value("${app.weather.api-url}")
	private String apiUrl;

	/** How many days ahead the provider will ask for a forecast (Open-Meteo free reaches ~16). */
	@Value("${app.weather.forecast-horizon-days}")
	private int horizonDays;

	/** Cache TTL — set to the upstream refresh rate (default 1h). See {@link CachingWeatherProvider}. */
	@Value("${app.weather.cache-ttl}")
	private Duration cacheTtl;

	@Bean
	WeatherProvider weatherProvider(Clock clock) {
		RestClient restClient = RestClient.create(apiUrl);
		WeatherProvider upstream = new OpenMeteoWeatherProvider(restClient, clock, horizonDays);
		return new CachingWeatherProvider(upstream, cacheTtl);
	}

}
