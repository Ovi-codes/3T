package ro.threet.run.appinfo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppInfoRepository extends JpaRepository<AppInfo, String> {
}