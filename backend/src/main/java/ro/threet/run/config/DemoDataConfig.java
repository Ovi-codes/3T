package ro.threet.run.config;

import java.util.Arrays;
import java.util.stream.Stream;

import org.flywaydb.core.api.Location;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The opt-in demo seed (#44). With {@code DEMO_DATA=true} ({@code app.demo-data}), Flyway also runs
 * the scripts in {@code db/demo} — a past run with ranked runners and a demo account — so the
 * leaderboard can be shown without typing data in. Off by default: tests and prod never get the fake
 * runners, and turning it on adds to the real schema migrations rather than replacing them.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("app.demo-data")
class DemoDataConfig {

	static final String DEMO_LOCATION = "classpath:db/demo";

	@Bean
	FlywayConfigurationCustomizer demoDataLocation() {
		return configuration -> configuration.locations(
				Stream.concat(Arrays.stream(configuration.getLocations()), Stream.of(new Location(DEMO_LOCATION)))
						.toArray(Location[]::new));
	}

}
