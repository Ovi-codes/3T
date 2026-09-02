package ro.threet.run.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.threet.run.registration.RegistrationService;

/**
 * A user's control over their own personal data (GDPR, charter §7): export everything held about
 * them, or erase their account outright. Both span the account (this package) and its registrations
 * (the registration package); this service is the single place that joins the two, so neither
 * package reaches across into the other's tables.
 */
@Service
public class AccountPrivacyService {

	private final AppUserRepository users;
	private final RegistrationService registrations;

	AccountPrivacyService(AppUserRepository users, RegistrationService registrations) {
		this.users = users;
		this.registrations = registrations;
	}

	/**
	 * Everything held about the account, for a data-portability download: the account record plus
	 * every registration made under it.
	 *
	 * @param userId the current account (never null — the endpoint requires authentication)
	 */
	@Transactional(readOnly = true)
	public AccountDataExport export(Long userId) {
		AppUser user = users.findById(userId)
				.orElseThrow(() -> new IllegalStateException("Authenticated account not found: " + userId));
		AccountDataExport.Account account = new AccountDataExport.Account(
				user.getId(), user.getEmail(), user.getCreatedAt());
		return new AccountDataExport(account, registrations.exportForUser(userId));
	}

	/**
	 * Erase the account: its registrations first (they hold a foreign key to it, and their name +
	 * email are personal data), then the account row itself. One transaction, so it is
	 * all-or-nothing — a half-deleted account never survives a failure partway through.
	 *
	 * @param userId the account being erased
	 */
	@Transactional
	public void erase(Long userId) {
		registrations.deleteForUser(userId);
		users.deleteById(userId);
	}

}
