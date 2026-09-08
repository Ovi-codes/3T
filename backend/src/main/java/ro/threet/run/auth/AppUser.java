package ro.threet.run.auth;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A registered account. Owned by the local {@link AuthProvider} — the only fields stored are the
 * person's name, their email, and a BCrypt hash of the password (GDPR data minimisation, charter
 * §7); the plaintext password never lives here. If auth later moves to Entra External ID, this
 * table goes with the local provider and the rest of the app keeps talking to {@link AccountPrincipal}.
 * Every account has a {@code name} (captured at sign-up since Increment 7, issue #38, and validated
 * at the API).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected AppUser() {
		// for JPA
	}

	public AppUser(String email, String name, String passwordHash) {
		this.email = email;
		this.name = name;
		this.passwordHash = passwordHash;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getName() {
		return name;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
