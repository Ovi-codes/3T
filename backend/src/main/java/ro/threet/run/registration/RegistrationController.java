package ro.threet.run.registration;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RegistrationController {

	private final RegistrationService registrationService;

	RegistrationController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	/**
	 * Register for an event. {@code @Valid} enforces name + email before the service runs;
	 * business rules (unknown/past event, duplicate) come back as errors from the service. A
	 * success is 201 with the created registration and its {@code Location}.
	 */
	@PostMapping("/registrations")
	public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
		RegistrationResponse response = registrationService.register(request);
		return ResponseEntity.created(URI.create("/api/registrations/" + response.id())).body(response);
	}

}