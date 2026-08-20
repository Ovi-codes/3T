package ro.threet.run.appinfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single row of {@code app_info}, written by the V1 migration.
 */
@Entity
@Table(name = "app_info")
public class AppInfo {

	@Id
	@Column(name = "version", nullable = false, length = 32)
	private String version;

	protected AppInfo() {
		// for JPA
	}

	public AppInfo(String version) {
		this.version = version;
	}

	public String getVersion() {
		return version;
	}

}