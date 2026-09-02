package ro.threet.run.auth;

import java.time.OffsetDateTime;
import java.util.List;

import ro.threet.run.registration.RegistrationExport;

/**
 * A user's full personal data, assembled for a GDPR data-portability export (charter §7): the
 * account itself plus every registration made under it. This is what the user downloads — a
 * structured, machine-readable form (JSON) they can keep or take elsewhere.
 *
 * The BCrypt password hash is deliberately absent: it is not the user's data to port, and handing
 * it out would weaken their security rather than serve their rights.
 */
public record AccountDataExport(Account account, List<RegistrationExport> registrations) {

	/** The account's own record — never the password hash. */
	public record Account(Long id, String email, OffsetDateTime createdAt) {
	}

}
