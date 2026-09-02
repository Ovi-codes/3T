package ro.threet.run.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own personal data (GDPR rights, charter §7): download everything held about
 * them ({@code GET /api/me/export}) or delete their account ({@code DELETE /api/me}). Both require a
 * session — deny-by-default in {@link SecurityConfig} closes them to anonymous callers with a 401,
 * so there is no anonymous branch to guard here.
 */
@RestController
@RequestMapping("/api/me")
class AccountPrivacyController {

	private final AccountPrivacyService accountPrivacyService;

	AccountPrivacyController(AccountPrivacyService accountPrivacyService) {
		this.accountPrivacyService = accountPrivacyService;
	}

	/** Right to data portability: the account and its registrations as a downloadable JSON file. */
	@GetMapping("/export")
	public ResponseEntity<AccountDataExport> export(@AuthenticationPrincipal AccountPrincipal principal) {
		AccountDataExport data = accountPrivacyService.export(principal.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"threet-run-my-data.json\"")
				.body(data);
	}

	/**
	 * Right to erasure: delete the account and its registrations, then tear down the now-orphaned
	 * session so the deleted principal can't keep acting — invalidate the session, clear the
	 * security context, and expire the cookie. 204, nothing to return.
	 */
	@DeleteMapping
	public ResponseEntity<Void> erase(@AuthenticationPrincipal AccountPrincipal principal,
			HttpServletRequest request, HttpServletResponse response) {
		accountPrivacyService.erase(principal.id());
		endSession(request, response);
		return ResponseEntity.noContent().build();
	}

	private static void endSession(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();

		Cookie cookie = new Cookie("JSESSIONID", "");
		cookie.setPath("/");
		cookie.setMaxAge(0);
		response.addCookie(cookie);
	}

}
