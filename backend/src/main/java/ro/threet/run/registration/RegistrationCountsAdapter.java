package ro.threet.run.registration;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ro.threet.run.event.RegistrationCounts;

import static java.util.stream.Collectors.toMap;

/**
 * Answers the events slice's {@link RegistrationCounts} seam from the registration table. Lives here,
 * next to the data it reads, so the events package never has to know the registration repository
 * exists (charter §3).
 */
@Component
class RegistrationCountsAdapter implements RegistrationCounts {

	private final RegistrationRepository registrations;

	RegistrationCountsAdapter(RegistrationRepository registrations) {
		this.registrations = registrations;
	}

	@Override
	@Transactional(readOnly = true)
	public long forEvent(Long eventId) {
		return registrations.countByEventId(eventId);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, Long> forEvents(Collection<Long> eventIds) {
		if (eventIds.isEmpty()) {
			// `in ()` is not valid SQL — and with nothing to count there is nothing to ask.
			return Map.of();
		}
		return registrations.countByEventIdIn(eventIds).stream()
				.collect(toMap(EventRegistrationCount::eventId, EventRegistrationCount::total));
	}

}
