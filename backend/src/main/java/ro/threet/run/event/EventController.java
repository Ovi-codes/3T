package ro.threet.run.event;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EventController {

	private final EventService eventService;

	EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping("/events")
	public List<EventResponse> events() {
		return eventService.upcomingEvents();
	}

}