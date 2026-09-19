package ro.threet.run.event;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin event management (issues #57, #58). Lives under {@code /api/admin/**}, which
 * {@code SecurityConfig} gates on {@code ROLE_ADMIN} — this server-side check is the real boundary;
 * the Angular role guard only hides the controls. {@code @Valid} enforces name + start before the
 * service runs; every other rule (the future-start check, what may still be edited, when a delete is
 * allowed) is the service's job and comes back as a 400/404/409 in the shared error envelope.
 *
 * <p>Delete and cancel are two different actions on purpose — see
 * <a href="../../../../../../../docs/adr/0001-cancel-vs-hard-delete-events.md">ADR-0001</a>.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminEventController {

	private final EventService eventService;

	AdminEventController(EventService eventService) {
		this.eventService = eventService;
	}

	/** The admin's schedule: upcoming runs, cancelled ones included, each with its live count. */
	@GetMapping("/events")
	public List<AdminEventResponse> events() {
		return eventService.adminEvents();
	}

	@PostMapping("/events")
	public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
		EventResponse created = eventService.createEvent(request);
		return ResponseEntity.created(URI.create("/api/events/" + created.id())).body(created);
	}

	@PutMapping("/events/{id}")
	public AdminEventResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEventRequest request) {
		return eventService.updateEvent(id, request);
	}

	/** Hard delete — refused with a 409 unless the run has no registrations at all (ADR-0001). */
	@DeleteMapping("/events/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		eventService.deleteEvent(id);
		return ResponseEntity.noContent().build();
	}

	/** Call the run off: terminal, keeps every registration, and emails the registrants. */
	@PostMapping("/events/{id}/cancel")
	public AdminEventResponse cancel(@PathVariable Long id) {
		return eventService.cancelEvent(id);
	}

}
