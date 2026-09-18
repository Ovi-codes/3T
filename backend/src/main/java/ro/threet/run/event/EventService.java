package ro.threet.run.event;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.threet.run.location.Location;
import ro.threet.run.location.LocationRepository;

@Service
public class EventService {

	/** Runs are entered and displayed in local Bucharest time; the DB stores the resulting instant. */
	private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");

	private final EventRepository eventRepository;
	private final LocationRepository locationRepository;
	private final Clock clock;

	EventService(EventRepository eventRepository, LocationRepository locationRepository, Clock clock) {
		this.eventRepository = eventRepository;
		this.locationRepository = locationRepository;
		this.clock = clock;
	}

	/**
	 * Upcoming events (starting now or later), soonest first, as DTOs. "Now" comes from the
	 * injected {@link Clock} so the rule is testable against a fixed instant.
	 */
	@Transactional(readOnly = true)
	public List<EventResponse> upcomingEvents() {
		OffsetDateTime now = OffsetDateTime.now(clock);
		return eventRepository.findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(now)
				.stream()
				.map(EventResponse::from)
				.toList();
	}

	/**
	 * Create a run (admin only — the authorisation boundary is in {@code SecurityConfig}; issue #57).
	 * The entered wall-clock time is interpreted in Europe/Bucharest and persisted as the resulting
	 * instant. The start must be in the future (checked against the app clock); a past start is a
	 * {@link EventValidationException} on the {@code startDateTime} field and nothing is saved. The
	 * location auto-binds to the sole existing row — there is no location picker for V1's single
	 * Bucharest location (the {@link Location} seam keeps multi-location a data change later).
	 */
	@Transactional
	public EventResponse createEvent(CreateEventRequest request) {
		OffsetDateTime start = request.startDateTime().atZone(BUCHAREST).toOffsetDateTime();
		if (!start.isAfter(OffsetDateTime.now(clock))) {
			throw new EventValidationException("startDateTime", "The start must be in the future.");
		}
		Location location = locationRepository.findAll().stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("No location is configured to host events."));
		Event saved = eventRepository.save(new Event(location, request.name().trim(), start));
		return EventResponse.from(saved);
	}

}
