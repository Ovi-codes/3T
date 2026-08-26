package ro.threet.run.event;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

	private final EventRepository eventRepository;
	private final Clock clock;

	EventService(EventRepository eventRepository, Clock clock) {
		this.eventRepository = eventRepository;
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

}