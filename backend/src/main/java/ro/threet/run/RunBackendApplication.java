package ro.threet.run;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Auth is fully owned by SecurityConfig + the AuthProvider seam, so we opt out of Spring Boot's
// default in-memory user (and its generated-password warning) — that account is unreachable with
// form and basic login disabled, and we don't want a stray credential in the context.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class RunBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(RunBackendApplication.class, args);
	}

}
