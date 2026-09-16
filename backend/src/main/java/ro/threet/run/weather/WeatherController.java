package ro.threet.run.weather;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The forecast for a single event, on its own endpoint so that the hero can load the forecast independently
 * and degrade on its own without blocking the list.
 *
 * <p>Public (a visitor sees the next run's forecast): {@code GET /api/events/**} is permitted in
 * {@code SecurityConfig}. Unknown event id → 404; a found event always answers 200 with a
 * {@link ForecastResponse} whose {@code available} flag says whether there's a forecast to show.
 */
@RestController
@RequestMapping("/api")
public class WeatherController {

	private final WeatherService weatherService;

	WeatherController(WeatherService weatherService) {
		this.weatherService = weatherService;
	}

	@GetMapping("/events/{id}/forecast")
	public ResponseEntity<ForecastResponse> forecast(@PathVariable long id) {
		return weatherService.forecastForEvent(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
