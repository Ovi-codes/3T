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
 * A person's registration for an {@link Event}, stored per (event, participant) — the shape that
 * lets results and account linkage share one row (charter §3 seam).
 *
 * Always carries name + email. {@code user_id} attributes the registration to a signed-in account
 * and is null for an anonymous one, so the core loop stays anonymous while a logged-in registration
 * links to its owner. {@code finish_time} remains unmapped — it belongs to V2 (results); Hibernate
 * validates only mapped columns against the schema, so leaving it off is fine.
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