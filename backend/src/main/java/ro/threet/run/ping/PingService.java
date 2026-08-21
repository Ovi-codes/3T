package ro.threet.run.ping;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.threet.run.appinfo.AppInfo;
import ro.threet.run.appinfo.AppInfoRepository;

@Service
public class PingService {

	private final AppInfoRepository appInfoRepository;

	PingService(AppInfoRepository appInfoRepository) {
		this.appInfoRepository = appInfoRepository;
	}

	/**
	 * Reads the application version straight from the database, so a successful response
	 * proves the migration ran and the connection works.
	 */
	@Transactional(readOnly = true)
	public PingResponse ping() {
		String version = appInfoRepository.findAll()
				.stream()
				.findFirst()
				.map(AppInfo::getVersion)
				.orElseThrow(() -> new IllegalStateException(
						"app_info is empty - the V1 migration did not run against this database"));
		return new PingResponse("ok", version);
	}

}