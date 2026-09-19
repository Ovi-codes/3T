package ro.threet.run.event;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin event management (issue #57). Lives under {@code /api/admin/**}, which {@code SecurityConfig}
 * gates on {@code ROLE_ADMIN} — this server-side check is the real boundary; the Angular role guard
 * only hides the controls. {@code @Valid} enforces name + start before the service runs; the
 * future-start rule and the location binding are the service's job.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminEventController {

	private final EventService eventService;

	AdminEventController(EventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping("/events")
	public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
		EventResponse created = eventService.createEvent(request);
		return ResponseEntity.created(URI.create("/api/events/" + created.id())).body(created);
	}

}
