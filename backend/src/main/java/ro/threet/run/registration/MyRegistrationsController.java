package ro.threet.run.registration;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ro.threet.run.auth.AccountPrincipal;

/**
 * The dashboard's data source: the current user's own registrations, split into upcoming and past.
 *
 * Unlike the public registration POST, this endpoint requires a session — it is not enumerated in
 * {@code SecurityConfig}, so the deny-by-default rule closes it and an anonymous request gets a plain
 * 401 (CS-6, server side). Because it is authenticated, {@code principal} is always present.
 */
@RestController
@RequestMapping("/api")
public class MyRegistrationsController {

	private final RegistrationService registrationService;

	MyRegistrationsController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@GetMapping("/me/registrations")
	public MyRegistrationsResponse myRegistrations(@AuthenticationPrincipal AccountPrincipal principal) {
		return registrationService.myRegistrations(principal.id());
	}

	/** Record or correct the caller's finish time on one of their own past registrations (#43). */
	@PutMapping("/me/registrations/{id}/finish-time")
	public MyRegistration recordFinishTime(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable Long id, @Valid @RequestBody FinishTimeRequest request) {
		return registrationService.recordFinishTime(principal.id(), id, request.finishTimeSeconds());
	}

}
