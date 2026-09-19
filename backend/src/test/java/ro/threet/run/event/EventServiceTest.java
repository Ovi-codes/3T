package ro.threet.run.event;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import ro.threet.run.location.Location;
import ro.threet.run.location.LocationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The event rules that don't need a database: which events each audience sees, and what an admin is
 * allowed to do to one (issues #57, #58).
 *
 * The upcoming-events filter itself lives in the repository query name; the service's job is to feed
 * it "now" from the injected clock, to hold the edit / delete / cancel rules, and to keep cancellation
 * notification out of the cancelling transaction. Real exclusion, ordering and cascade behaviour
 * against data are proven by the integration test on a live Postgres.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-21T09:00:00Z");
	private final Clock fixedClock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

	@Mock
	private EventRepository eventRepository;

	@Mock
	private LocationRepository locationRepository;

	@Mock
	private RegistrationCounts registrationCounts;

	@Mock
	private ApplicationEventPublisher events;

	@Captor
	private ArgumentCaptor<OffsetDateTime> cutoffCaptor;

	@Captor
	private ArgumentCaptor<Event> savedCaptor;

	private EventService service() {
		return new EventService(eventRepository, locationRepository, registrationCounts, events, fixedClock);
	}

	// --- Reading -------------------------------------------------------------------------------

	@Test
	void queriesUpcomingScheduledEventsFromTheClockNowAndMapsInOrder() {
		EventService service = service();
		Event soon = event(1L, "soonest", NOW.plusDays(1));
		Event later = event(2L, "next week", NOW.plusDays(8));
		given(eventRepository.findByStatusAndStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(
				eq(EventStatus.SCHEDULED), cutoffCaptor.capture()))
				.willReturn(List.of(soon, later));

		List<EventResponse> result = service.upcomingEvents();

		// the cutoff is exactly the clock's "now" — past events are asked to be excluded
		assertThat(cutoffCaptor.getValue()).isEqualTo(NOW);
		// order from the repository (ascending) is preserved into the DTOs
		assertThat(result).extracting(EventResponse::id).containsExactly(1L, 2L);
		assertThat(result).extracting(EventResponse::startDateTime).isSorted();
		assertThat(result.get(0)).isEqualTo(
				new EventResponse(1L, "soonest", NOW.plusDays(1), "Tineretului Park", "Bucharest"));
	}

	@Test
	void adminEventsKeepCancelledOnesAndCarryTheirLiveRegistrationCount() {
		Event scheduled = persisted(1L, "Autumn 5k", NOW.plusDays(1));
		Event cancelled = persisted(2L, "Rained off", NOW.plusDays(2));
		cancelled.cancel();
		given(eventRepository.findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(NOW))
				.willReturn(List.of(scheduled, cancelled));
		given(registrationCounts.forEvents(List.of(1L, 2L))).willReturn(Map.of(1L, 3L));

		List<AdminEventResponse> result = service().adminEvents();

		assertThat(result).extracting(AdminEventResponse::id).containsExactly(1L, 2L);
		assertThat(result.get(0).status()).isEqualTo(EventStatus.SCHEDULED);
		assertThat(result.get(0).registrationCount()).isEqualTo(3L);
		// An event nobody has registered for still reports a count — zero, not a gap.
		assertThat(result.get(1).status()).isEqualTo(EventStatus.CANCELLED);
		assertThat(result.get(1).registrationCount()).isZero();
	}

	// --- Creating ------------------------------------------------------------------------------

	@Test
	void createInterpretsTheEnteredTimeAsBucharestAndBindsTheSoleLocation() {
		Location location = mock(Location.class);
		given(location.getName()).willReturn("Tineretului Park");
		given(location.getCity()).willReturn("Bucharest");
		given(locationRepository.findAll()).willReturn(List.of(location));
		given(eventRepository.save(any(Event.class))).willAnswer(call -> call.getArgument(0));

		// 21 Aug 2026 18:00 in Bucharest (EEST, UTC+3) → 15:00Z, comfortably after the fixed 09:00Z now.
		EventResponse response = service().createEvent(
				new CreateEventRequest("Tineretului parkrun", LocalDateTime.parse("2026-08-21T18:00")));

		verify(eventRepository).save(savedCaptor.capture());
		Event saved = savedCaptor.getValue();
		assertThat(saved.getName()).isEqualTo("Tineretului parkrun");
		assertThat(saved.getLocation()).isSameAs(location);
		// A new run starts out scheduled — cancellation is an explicit later act.
		assertThat(saved.getStatus()).isEqualTo(EventStatus.SCHEDULED);
		// The entered wall-clock time keeps the Bucharest offset (+03:00 in August).
		assertThat(saved.getStartDateTime())
				.isEqualTo(OffsetDateTime.parse("2026-08-21T18:00+03:00"));
		assertThat(saved.getStartDateTime().toInstant())
				.isEqualTo(OffsetDateTime.parse("2026-08-21T15:00Z").toInstant());

		assertThat(response.name()).isEqualTo("Tineretului parkrun");
		assertThat(response.locationName()).isEqualTo("Tineretului Park");
		assertThat(response.city()).isEqualTo("Bucharest");
	}

	@Test
	void createRejectsAStartInThePastAndSavesNothing() {
		// 21 Aug 2026 10:00 in Bucharest → 07:00Z, before the fixed 09:00Z now.
		assertThatThrownBy(() -> service().createEvent(
				new CreateEventRequest("Past run", LocalDateTime.parse("2026-08-21T10:00"))))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).field())
						.isEqualTo("startDateTime"));

		verify(eventRepository, never()).save(any());
	}

	// --- Editing -------------------------------------------------------------------------------

	@Test
	void updateRenamesAndReschedulesAnUpcomingEvent() {
		Event event = persisted(7L, "Autumn 5k", NOW.plusDays(3));
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));
		given(eventRepository.save(any(Event.class))).willAnswer(call -> call.getArgument(0));
		given(registrationCounts.forEvent(7L)).willReturn(4L);

		AdminEventResponse response = service().updateEvent(7L,
				new UpdateEventRequest("  Autumn Night 5k  ", LocalDateTime.parse("2026-09-01T18:30")));

		// Name is trimmed; the entered wall-clock time keeps the Bucharest offset (+03:00 in September).
		assertThat(event.getName()).isEqualTo("Autumn Night 5k");
		assertThat(event.getStartDateTime()).isEqualTo(OffsetDateTime.parse("2026-09-01T18:30+03:00"));
		assertThat(response.name()).isEqualTo("Autumn Night 5k");
		assertThat(response.registrationCount()).isEqualTo(4L);
		verify(eventRepository).save(event);
	}

	@Test
	void updateRejectsAnUnknownEvent() {
		given(eventRepository.findById(404L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service().updateEvent(404L, anUpdate()))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).status())
						.isEqualTo(HttpStatus.NOT_FOUND));

		verify(eventRepository, never()).save(any());
	}

	@Test
	void updateRejectsAStartInThePastAndChangesNothing() {
		Event event = persisted(7L, "Autumn 5k", NOW.plusDays(3));
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));

		// 21 Aug 2026 10:00 in Bucharest → 07:00Z, before the fixed 09:00Z now.
		assertThatThrownBy(() -> service().updateEvent(7L,
				new UpdateEventRequest("Backdated", LocalDateTime.parse("2026-08-21T10:00"))))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> {
					assertThat(((EventException) thrown).status()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(((EventException) thrown).field()).isEqualTo("startDateTime");
				});

		assertThat(event.getName()).isEqualTo("Autumn 5k");
		verify(eventRepository, never()).save(any());
	}

	@Test
	void updateRejectsACancelledEventBecauseCancellationIsTerminal() {
		Event event = persisted(7L, "Rained off", NOW.plusDays(3));
		event.cancel();
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));

		assertThatThrownBy(() -> service().updateEvent(7L, anUpdate()))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).status())
						.isEqualTo(HttpStatus.CONFLICT));

		assertThat(event.getName()).isEqualTo("Rained off");
		verify(eventRepository, never()).save(any());
	}

	@Test
	void updateRejectsAnEventThatHasAlreadyRun() {
		Event event = persisted(7L, "Last week's run", NOW.minusDays(3));
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));

		assertThatThrownBy(() -> service().updateEvent(7L, anUpdate()))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).status())
						.isEqualTo(HttpStatus.CONFLICT));

		verify(eventRepository, never()).save(any());
	}

	// --- Deleting (the "created in error" escape hatch — see ADR-0001) -------------------------

	@Test
	void deleteHardRemovesAnEventNobodyHasRegisteredFor() {
		Event event = persisted(7L, "Created by mistake", NOW.plusDays(3));
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));
		given(registrationCounts.forEvent(7L)).willReturn(0L);

		service().deleteEvent(7L);

		verify(eventRepository).delete(event);
	}

	@Test
	void deleteIsRefusedWhenPeopleAreRegisteredSoNoPersonalDataIsDestroyed() {
		Event event = persisted(7L, "Autumn 5k", NOW.plusDays(3));
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));
		given(registrationCounts.forEvent(7L)).willReturn(2L);

		assertThatThrownBy(() -> service().deleteEvent(7L))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).status())
						.isEqualTo(HttpStatus.CONFLICT));

		verify(eventRepository, never()).delete(any());
	}

	@Test
	void deleteRejectsAnUnknownEvent() {
		given(eventRepository.findById(404L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service().deleteEvent(404L))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).status())
						.isEqualTo(HttpStatus.NOT_FOUND));

		verify(eventRepository, never()).delete(any());
		verifyNoInteractions(events);
	}

	// --- Cancelling (the soft, terminal path — see ADR-0001) -----------------------------------

	@Test
	void cancelMarksTheEventCancelledAndAnnouncesItForTheRegistrantEmails() {
		Event event = persisted(7L, "Autumn 5k", NOW.plusDays(3));
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));
		given(eventRepository.save(any(Event.class))).willAnswer(call -> call.getArgument(0));
		given(registrationCounts.forEvent(7L)).willReturn(2L);

		AdminEventResponse response = service().cancelEvent(7L);

		assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
		assertThat(response.status()).isEqualTo(EventStatus.CANCELLED);
		assertThat(response.registrationCount()).isEqualTo(2L);
		verify(eventRepository).save(event);
		// Notification is announced, never sent inline — the listener runs after the commit, so a
		// failing mail server can't roll the cancellation back (ADR-0001).
		verify(events).publishEvent(new EventCancelled(7L));
	}

	@Test
	void cancelIsRefusedTwiceBecauseThereIsNoUnCancel() {
		Event event = persisted(7L, "Rained off", NOW.plusDays(3));
		event.cancel();
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));

		assertThatThrownBy(() -> service().cancelEvent(7L))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).status())
						.isEqualTo(HttpStatus.CONFLICT));

		verify(eventRepository, never()).save(any());
		verifyNoInteractions(events);
	}

	@Test
	void cancelIsRefusedForAnEventThatHasAlreadyRun() {
		Event event = persisted(7L, "Last week's run", NOW.minusDays(3));
		given(eventRepository.findById(7L)).willReturn(Optional.of(event));

		assertThatThrownBy(() -> service().cancelEvent(7L))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).status())
						.isEqualTo(HttpStatus.CONFLICT));

		assertThat(event.getStatus()).isEqualTo(EventStatus.SCHEDULED);
		verifyNoInteractions(events);
	}

	@Test
	void cancelRejectsAnUnknownEvent() {
		given(eventRepository.findById(anyLong())).willReturn(Optional.empty());

		assertThatThrownBy(() -> service().cancelEvent(404L))
				.isInstanceOf(EventException.class)
				.satisfies(thrown -> assertThat(((EventException) thrown).status())
						.isEqualTo(HttpStatus.NOT_FOUND));

		verifyNoInteractions(events);
	}

	// --- Fixtures ------------------------------------------------------------------------------

	private static UpdateEventRequest anUpdate() {
		return new UpdateEventRequest("Renamed", LocalDateTime.parse("2026-09-01T18:30"));
	}

	/** A stubbed read-only event, for the list-shaped tests. */
	private Event event(Long id, String name, OffsetDateTime start) {
		Location location = mock(Location.class);
		given(location.getName()).willReturn("Tineretului Park");
		given(location.getCity()).willReturn("Bucharest");
		Event event = mock(Event.class);
		given(event.getId()).willReturn(id);
		given(event.getName()).willReturn(name);
		given(event.getStartDateTime()).willReturn(start);
		given(event.getLocation()).willReturn(location);
		return event;
	}

	/**
	 * A real event as it would come back from the repository — a genuine instance (not a mock) so the
	 * tests can assert on the state the service actually mutated, with the id the DB would have set.
	 */
	private static Event persisted(Long id, String name, OffsetDateTime start) {
		// Lenient: the refusal tests never reach the DTO mapping, so the location stubs go unused there.
		Location location = mock(Location.class);
		lenient().when(location.getName()).thenReturn("Tineretului Park");
		lenient().when(location.getCity()).thenReturn("Bucharest");
		Event event = new Event(location, name, start);
		ReflectionTestUtils.setField(event, "id", id);
		return event;
	}

}
