package ro.threet.run.event;

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

import ro.threet.run.location.Location;

/**
 * A single occurrence of a run at a {@link Location} — the weekly 5k, on a given date.
 */
@Entity
@Table(name = "event")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "location_id", nullable = false)
	private Location location;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(name = "start_datetime", nullable = false)
	private OffsetDateTime startDateTime;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Event() {
		// for JPA
	}

	public Long getId() {
		return id;
	}

	public Location getLocation() {
		return location;
	}

	public String getName() {
		return name;
	}

	public OffsetDateTime getStartDateTime() {
		return startDateTime;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
