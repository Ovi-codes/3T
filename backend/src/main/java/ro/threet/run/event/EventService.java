package ro.threet.run.event;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
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
	private final RegistrationCounts registrationCounts;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	EventService(EventRepository eventRepository, LocationRepository locationRepository,
			RegistrationCounts registrationCounts, ApplicationEventPublisher eventPublisher, Clock clock) {
		this.eventRepository = eventRepository;
		this.locationRepository = locationRepository;
		this.registrationCounts = registrationCounts;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	/**
	 * What the public sees: upcoming events (starting now or later) that are still on, soonest first,
	 * as DTOs. Cancelled runs are excluded — the homepage stops offering a run the moment it's called
	 * off (ADR-0001). "Now" comes from the injected {@link Clock} so the rule is testable against a
	 * fixed instant.
	 */
	@Transactional(readOnly = true)
	public List<EventResponse> upcomingEvents() {
		OffsetDateTime now = OffsetDateTime.now(clock);
		return eventRepository
				.findByStatusAndStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(EventStatus.SCHEDULED, now)
				.stream()
				.map(EventResponse::from)
				.toList();
	}

	/**
	 * What an admin sees: the same upcoming window but cancelled runs included (so a called-off run
	 * stays on the schedule, read-only), each carrying its live registration count. The counts come
	 * back in a single query rather than one per event. Admin-only — {@code SecurityConfig} gates the
	 * {@code /api/admin/**} path this is served under.
	 */
	@Transactional(readOnly = true)
	public List<AdminEventResponse> adminEvents() {
		List<Event> upcoming =
				eventRepository.findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(OffsetDateTime.now(clock));
		Map<Long, Long> counts = registrationCounts.forEvents(upcoming.stream().map(Event::getId).toList());
		return upcoming.stream()
				.map(event -> AdminEventResponse.from(event, counts.getOrDefault(event.getId(), 0L)))
				.toList();
	}

	/**
	 * Create a run (admin only — the authorisation boundary is in {@code SecurityConfig}; issue #57).
	 * The entered wall-clock time is interpreted in Europe/Bucharest and persisted as the resulting
	 * instant. The start must be in the future (checked against the app clock); a past start is an
	 * {@link EventException} on the {@code startDateTime} field and nothing is saved. The location
	 * auto-binds to the sole existing row — there is no location picker for V1's single Bucharest
	 * location (the {@link Location} seam keeps multi-location a data change later).
	 */
	@Transactional
	public EventResponse createEvent(CreateEventRequest request) {
		OffsetDateTime start = requireFutureStart(request.startDateTime());
		Location location = locationRepository.findAll().stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("No location is configured to host events."));
		Event saved = eventRepository.save(new Event(location, request.name().trim(), start));
		return EventResponse.from(saved);
	}

	/**
	 * Rename and/or reschedule a run (admin only; issue #58). Only a run that is still ahead and
	 * still on can be edited: a cancelled one is terminal and an already-run one is history, so both
	 * are 409s that change nothing. The new start is read as Bucharest wall-clock time and must
	 * itself be in the future, exactly as on create.
	 *
	 * <p>If the edit <em>moves the start</em> (date or time), the registrants are emailed the new
	 * time by a listener that runs after the commit — a rename on its own notifies nobody. Same
	 * best-effort, post-commit delivery as a cancellation, for the same reason (ADR-0001).
	 *
	 * @throws EventException 404 unknown id, 409 not editable, 400 start not in the future
	 */
	@Transactional
	public AdminEventResponse updateEvent(Long id, UpdateEventRequest request) {
		Event event = require(id);
		requireEditable(event);
		OffsetDateTime start = requireFutureStart(request.startDateTime());
		OffsetDateTime previousStart = event.getStartDateTime();

		event.updateDetails(request.name().trim(), start);
		Event saved = eventRepository.save(event);
		// isEqual compares the instant, so a mere offset re-spelling of the same moment isn't a move.
		if (!previousStart.isEqual(start)) {
			eventPublisher.publishEvent(new EventRescheduled(id, previousStart, start));
		}
		return AdminEventResponse.from(saved, registrationCounts.forEvent(id));
	}

	/**
	 * Hard-remove a run — the "created in error" escape hatch, and the only path that deletes an
	 * event row (admin only; issue #58). Allowed <strong>only while nobody is registered</strong>: as
	 * soon as a registration exists, deleting would destroy someone else's personal data without
	 * telling them, so it is refused (409) and {@link #cancelEvent} is the way out instead. That split
	 * is ADR-0001, and it is what keeps admin removal consistent with account erasure (charter §7).
	 *
	 * @throws EventException 404 unknown id, 409 the event has registrations
	 */
	@Transactional
	public void deleteEvent(Long id) {
		Event event = require(id);
		long registered = registrationCounts.forEvent(id);
		if (registered > 0) {
			throw new EventException(HttpStatus.CONFLICT, EventException.EVENT_FIELD,
					"This run has %d registration(s) and can't be deleted — cancel it instead."
							.formatted(registered));
		}
		eventRepository.delete(event);
	}

	/**
	 * Call a run off (admin only; issue #58). Marks it {@code CANCELLED} — terminal, so a run that is
	 * already cancelled or already over is a 409 — keeping every registration row. The cancellation
	 * commits on its own; the registrant emails are sent by a listener that runs
	 * <em>after</em> the commit, so a failing mail server can never undo the cancel (ADR-0001).
	 *
	 * <p>A reason is required (enforced as {@code @NotBlank} at the controller boundary). It is plain
	 * text — the admin UI offers standard reasons and a free-text option, but the finished wording
	 * arrives as one string and travels on the {@link EventCancelled} event into the email.
	 *
	 * @throws EventException 404 unknown id, 409 already cancelled or already run
	 */
	@Transactional
	public AdminEventResponse cancelEvent(Long id, CancelEventRequest request) {
		Event event = require(id);
		requireEditable(event);

		event.cancel();
		Event saved = eventRepository.save(event);
		eventPublisher.publishEvent(new EventCancelled(id, request.reason().trim()));
		return AdminEventResponse.from(saved, registrationCounts.forEvent(id));
	}

	private Event require(Long id) {
		return eventRepository.findById(id).orElseThrow(() -> new EventException(HttpStatus.NOT_FOUND,
				EventException.EVENT_FIELD, "That run could not be found."));
	}

	/**
	 * The one definition of "an admin may still act on this run": it has not been cancelled (that is
	 * terminal) and it has not already happened. Shared by edit and cancel so the two can't drift.
	 */
	private void requireEditable(Event event) {
		if (event.isCancelled()) {
			throw new EventException(HttpStatus.CONFLICT, EventException.EVENT_FIELD,
					"This run has been cancelled and can no longer be changed.");
		}
		if (event.getStartDateTime().isBefore(OffsetDateTime.now(clock))) {
			throw new EventException(HttpStatus.CONFLICT, EventException.EVENT_FIELD,
					"This run has already taken place and can no longer be changed.");
		}
	}

	/** Read an entered wall-clock start as Bucharest time and insist it is still ahead of us. */
	private OffsetDateTime requireFutureStart(LocalDateTime entered) {
		OffsetDateTime start = entered.atZone(BUCHAREST).toOffsetDateTime();
		if (!start.isAfter(OffsetDateTime.now(clock))) {
			throw new EventException("startDateTime", "The start must be in the future.");
		}
		return start;
	}

}
