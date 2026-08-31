package ro.threet.run.registration;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ro.threet.run.event.Event;

/**
 * A person's registration for an {@link Event}. Stored per (event, participant) so results and
 * signed-in linkage slot in later (charter §3 seam); this increment only sets name + email.
 *
 * {@code user_id} is set when a signed-in user registers (Increment 3) and left null for an
 * anonymous registration — the core loop stays anonymous. {@code finish_time} is still unmapped;
 * it belongs to V2 (results). Hibernate validates mapped columns against the schema, so leaving
 * that one off is fine.
 */
@Entity
@Table(name = "registration")
public class Registration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 254)
	private String email;

	/** The account that made this registration, or null when it was made anonymously. */
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Registration() {
		// for JPA
	}

	public Registration(Event event, String name, String email) {
		this.event = event;
		this.name = name;
		this.email = email;
	}

	/** Attribute this registration to a signed-in account. */
	public void linkUser(Long userId) {
		this.userId = userId;
	}

	public Long getId() {
		return id;
	}

	public Event getEvent() {
		return event;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public Long getUserId() {
		return userId;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}