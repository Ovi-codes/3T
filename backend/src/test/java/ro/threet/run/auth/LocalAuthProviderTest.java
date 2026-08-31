package ro.threet.run.auth;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The local provider's credential rules in isolation: passwords are hashed (never stored raw),
 * emails are normalised so accounts are case-insensitive, a taken email is refused, and a login
 * failure is one indistinguishable outcome whether the email is unknown or the password is wrong.
 */
@ExtendWith(MockitoExtension.class)
class LocalAuthProviderTest {

	// A real encoder — the whole point is that hashing actually happens and verifies.
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Mock
	private AppUserRepository users;

	@Captor
	private ArgumentCaptor<AppUser> savedCaptor;

	private LocalAuthProvider provider() {
		return new LocalAuthProvider(users, passwordEncoder);
	}

	@Test
	void signupStoresANormalisedEmailAndAHashedPassword() {
		when(users.existsByEmail("ana@example.com")).thenReturn(false);
		when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

		AccountPrincipal principal = provider().signup("  Ana@Example.com  ", "correct horse");

		verify(users).save(savedCaptor.capture());
		AppUser saved = savedCaptor.getValue();
		assertThat(saved.getEmail()).isEqualTo("ana@example.com");
		assertThat(saved.getPasswordHash()).isNotEqualTo("correct horse");
		assertThat(passwordEncoder.matches("correct horse", saved.getPasswordHash())).isTrue();
		assertThat(principal.email()).isEqualTo("ana@example.com");
	}

	@Test
	void signupRejectsAnEmailThatAlreadyHasAnAccount() {
		when(users.existsByEmail("ana@example.com")).thenReturn(true);

		assertThatThrownBy(() -> provider().signup("ana@example.com", "correct horse"))
				.isInstanceOf(EmailAlreadyUsedException.class);

		verify(users, never()).save(any());
	}

	@Test
	void loginSucceedsWithTheCorrectPassword() {
		AppUser user = new AppUser("ana@example.com", passwordEncoder.encode("correct horse"));
		when(users.findByEmail("ana@example.com")).thenReturn(Optional.of(user));

		AccountPrincipal principal = provider().login("  ANA@example.com ", "correct horse");

		assertThat(principal.email()).isEqualTo("ana@example.com");
	}

	@Test
	void loginRejectsAWrongPassword() {
		AppUser user = new AppUser("ana@example.com", passwordEncoder.encode("correct horse"));
		when(users.findByEmail("ana@example.com")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> provider().login("ana@example.com", "wrong"))
				.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void loginRejectsAnUnknownEmail() {
		when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> provider().login("nobody@example.com", "correct horse"))
				.isInstanceOf(BadCredentialsException.class);
	}

}
