package ro.threet.run.location;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A place that hosts events. First-class from day one (charter §3 seam): Bucharest is the
 * only row for V1, but events belong to a location so multi-location later is data, not
 * schema change.
 */
@Entity
@Table(name = "location")
public class Location {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 120)
	private String city;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Location() {
		// for JPA
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCity() {
		return city;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}