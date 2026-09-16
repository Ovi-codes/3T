package ro.threet.run.weather;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;
import ro.threet.run.location.Location;

/**
 * Turns "the forecast for this event" into a coordinate + a date to ask the {@link WeatherProvider},
 * then into a {@link ForecastResponse}.
 */
@Service
public class WeatherService {

	/** V1 is Bucharest-only; the forecast day is the event's calendar day in its local zone. */
	private static final ZoneId EVENT_ZONE = ZoneId.of("Europe/Bucharest");

	private final EventRepository eventRepository;
	private final WeatherProvider weatherProvider;

	WeatherService(EventRepository eventRepository, WeatherProvider weatherProvider) {
		this.eventRepository = eventRepository;
		this.weatherProvider = weatherProvider;
	}

	/**
	 * The forecast for one event: {@link Optional#empty()} when the event id is unknown (the caller
	 * answers 404), otherwise a {@link ForecastResponse} — which is itself {@code available=false}
	 * when the location has no coordinates or no forecast is available for the day.
	 */
	@Transactional(readOnly = true)
	public Optional<ForecastResponse> forecastForEvent(long eventId) {
		return eventRepository.findById(eventId).map(this::forecastFor);
	}

	private ForecastResponse forecastFor(Event event) {
		Location location = event.getLocation();
		if (location.getLatitude() == null || location.getLongitude() == null) {
			return ForecastResponse.unavailable();
		}
		LocalDate date = event.getStartDateTime().atZoneSameInstant(EVENT_ZONE).toLocalDate();
		return weatherProvider
				.forecast(location.getLatitude().doubleValue(), location.getLongitude().doubleValue(), date)
				.map(ForecastResponse::of)
				.orElseGet(ForecastResponse::unavailable);
	}

}
