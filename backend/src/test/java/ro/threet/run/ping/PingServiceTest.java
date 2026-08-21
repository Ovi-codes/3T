package ro.threet.run.ping;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.threet.run.appinfo.AppInfo;
import ro.threet.run.appinfo.AppInfoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PingServiceTest {

	@Mock
	private AppInfoRepository appInfoRepository;

	@InjectMocks
	private PingService pingService;

	@Test
	void reportsOkWithTheVersionFromTheDatabase() {
		given(appInfoRepository.findAll()).willReturn(List.of(new AppInfo("1.2.3")));

		assertThat(pingService.ping()).isEqualTo(new PingResponse("ok", "1.2.3"));
	}

	@Test
	void failsLoudlyWhenTheTableIsEmpty() {
		given(appInfoRepository.findAll()).willReturn(List.of());

		assertThatIllegalStateException()
				.isThrownBy(() -> pingService.ping())
				.withMessageContaining("app_info is empty");
	}

}