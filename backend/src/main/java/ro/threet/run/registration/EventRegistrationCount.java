package ro.threet.run.registration;

/**
 * One row of "how many registrations does this event have" — the shape the grouped count query in
 * {@link RegistrationRepository} projects into. Internal to this package; the events slice sees only
 * the {@code Map} that {@link RegistrationCountsAdapter} builds from these.
 */
public record EventRegistrationCount(Long eventId, long total) {
}
