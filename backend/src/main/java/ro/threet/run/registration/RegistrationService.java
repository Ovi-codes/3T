package ro.threet.run.registration;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.threet.run.email.EmailSender;
import ro.threet.run.event.Event;
import ro.threet.run.event.EventRepository;

/**
 * The core loop: record a registration for an upcoming event and send its confirmation email.
 *
 * Persisting and sending happen in one transaction, so the two either both take effect or neither
 * does — if the email can't be handed off, the registration rolls back rather than leaving a
 * confirmed-but-unnotified row. Business rules are checked before either happens, so an invalid
 * request never persists a row or sends a message.
 */
@Service
public class RegistrationService {

	private final RegistrationRepository registrationRepository;
	private final EventRepository eventRepository;
	private final EmailSender emailSender;
	private final Clock clock;

	RegistrationService(RegistrationRepository registrationRepository, EventRepository eventRepository,
			EmailSender emailSender, Clock clock) {
		this.registrationRepository = registrationRepository;
		this.eventRepository = eventRepository;
		this.emailSender = emailSender;
		this.clock = clock;
	}

	/**
	 * Record a registration for an upcoming event and send its confirmation email, atomically.
	 *
	 * @param request the submitted event id, name, and email (already bean-validated at the boundary)
	 * @param userId  the signed-in account making the registration, or null when anonymous — the
	 *                anonymous core loop passes null and is unchanged
	 * @return the recorded registration, echoed back with its event details for the confirmation view
	 * @throws RegistrationException if the event is unknown (404), already past (400), or the email
	 *                               is already registered for it (409) — nothing is saved or sent
	 */
	@Transactional
	public RegistrationResponse register(RegistrationRequest request, Long userId) {
		String name = request.name().trim();
		String email = request.email().trim().toLowerCase(Locale.ROOT);

		Event event = eventRepository.findById(request.eventId())
				.orElseThrow(() -> new RegistrationException(HttpStatus.NOT_FOUND, "eventId",
						"That event could not be found."));

		if (event.getStartDateTime().isBefore(OffsetDateTime.now(clock))) {
			throw new RegistrationException(HttpStatus.BAD_REQUEST, "eventId",
					"The selected run has already taken place.");
		}

		if (registrationRepository.existsByEventIdAndEmailIgnoreCase(event.getId(), email)) {
			throw new RegistrationException(HttpStatus.CONFLICT, "email",
					"The email is already registered for this run.");
		}

		Registration registration = new Registration(event, name, email);
		if (userId != null) {
			registration.linkUser(userId);
		}
		registration = registrationRepository.save(registration);

		ConfirmationEmail confirmation = ConfirmationEmail.forRegistration(registration);
		emailSender.send(email, confirmation.subject(), confirmation.body());

		return RegistrationResponse.from(registration);
	}

	/**
	 * The signed-in user's registrations, split into upcoming (event starting now or later) and past
	 * (already gone by), for the dashboard. The cutoff comes from the injected {@link Clock} so the
	 * split is testable against a fixed instant. The repository returns rows newest-run-first; that
	 * order is what "past" wants (most recent at the top), so past keeps it and upcoming is reversed
	 * to soonest-first (the next run at the top).
	 *
	 * @param userId the current account (never null — the endpoint requires authentication)
	 */
	@Transactional(readOnly = true)
	public MyRegistrationsResponse myRegistrations(Long userId) {
		OffsetDateTime now = OffsetDateTime.now(clock);

		List<MyRegistration> upcoming = new ArrayList<>();
		List<MyRegistration> past = new ArrayList<>();
		for (Registration registration : registrationRepository.findByUserIdWithEvent(userId)) {
			if (registration.getEvent().getStartDateTime().isBefore(now)) {
				past.add(MyRegistration.from(registration));
			} else {
				upcoming.add(MyRegistration.from(registration));
			}
		}
		// Rows arrive newest-first; upcoming reads best soonest-first.
		Collections.reverse(upcoming);

		return new MyRegistrationsResponse(upcoming, past);
	}

}