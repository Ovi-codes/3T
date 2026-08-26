package ro.threet.run.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web-layer contract: a payload that fails validation is a 400 carrying per-field messages,
 * and the request never reaches the service — so, crucially, no registration is attempted and no
 * email is sent for bad input. The service is mocked; the real one is covered by the integration
 * test.
 */
@WebMvcTest(RegistrationController.class)
class RegistrationControllerValidationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistrationService registrationService;

	@Test
	void rejectsMalformedEmailAndBlankNameWithFieldErrors() throws Exception {
		String body = """
				{"eventId": 1, "name": "  ", "email": "not-an-email"}""";

		mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists())
				.andExpect(jsonPath("$.errors.email").exists());

		verifyNoInteractions(registrationService);
	}

	@Test
	void rejectsMissingEmailWithFieldError() throws Exception {
		String body = """
				{"eventId": 1, "name": "Ana"}""";

		mockMvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.email").exists());

		verifyNoInteractions(registrationService);
	}

}