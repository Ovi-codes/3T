package ro.threet.run.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A grantable authority (e.g. {@code ROLE_USER}, {@code ROLE_ADMIN}). The app looks them up by name
 * and links them to an {@link AppUser}. Kept in the auth package because roles are part of "who is signed in".
 */
@Entity
@Table(name = "role")
public class Role {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 60)
	private String name;

	protected Role() {
		// for JPA
	}

	Role(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}
