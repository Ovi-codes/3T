package ro.threet.run.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application clock. Injected wherever "now" is needed (e.g. the upcoming-events
 * cutoff) so tests can substitute a fixed instant instead of the wall clock.
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}