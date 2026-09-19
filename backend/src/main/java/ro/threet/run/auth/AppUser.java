package ro.threet.run.auth;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * A registered account. Owned by the local {@link AuthProvider} — the only fields stored are the
 * person's name, their email, and a BCrypt hash of the password (GDPR data minimisation, charter
 * §7). If auth later moves to Entra External ID, this table goes with the local provider and the
 * rest of the app keeps talking to {@link AccountPrincipal}.
 * Every account has a {@code name} (captured at sign-up and validated at the API).
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

	// Eager because roles are resolved into the principal at authentication time, and
	// the principal is then self-describing in the session — no per-request DB hit for authorities.
	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "user_roles",
			joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id"))
	private Set<Role> roles = new HashSet<>();

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

	/** The roles granted to this account. The join rows are managed through this collection. */
	public Set<Role> getRoles() {
		return roles;
	}

	/** Whether this account already holds the named role. */
	public boolean hasRole(String roleName) {
		return roles.stream().anyMatch(role -> role.getName().equals(roleName));
	}

	/** Grant a role (idempotent — the underlying set dedupes). */
	public void addRole(Role role) {
		roles.add(role);
	}

	/** Revoke a role by name (no-op if not held); drops the join row when the account is saved. */
	public void removeRole(String roleName) {
		roles.removeIf(role -> role.getName().equals(roleName));
	}

}
