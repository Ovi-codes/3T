package ro.threet.run.event;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	/** Stored as its name, not its ordinal, so the column reads plainly and reordering is harmless. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EventStatus status = EventStatus.SCHEDULED;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Event() {
		// for JPA
	}

	/**
	 * Create a new run at a location — scheduled, which is the only state a run starts in.
	 * {@code createdAt} is set by the DB default on insert.
	 */
	public Event(Location location, String name, OffsetDateTime startDateTime) {
		this.location = location;
		this.name = name;
		this.startDateTime = startDateTime;
	}

	/**
	 * Rename and/or reschedule the run. The caller owns the rules about <em>when</em> this is allowed
	 * (not cancelled, not already run, the new start still in the future) — see {@link EventService}.
	 */
	public void updateDetails(String name, OffsetDateTime startDateTime) {
		this.name = name;
		this.startDateTime = startDateTime;
	}

	/**
	 * Call the run off. Terminal — nothing here turns it back into a scheduled run (ADR-0001). The
	 * registrations stay; notifying the people who hold them is the caller's job.
	 */
	public void cancel() {
		this.status = EventStatus.CANCELLED;
	}

	public boolean isCancelled() {
		return status == EventStatus.CANCELLED;
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

	public EventStatus getStatus() {
		return status;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
