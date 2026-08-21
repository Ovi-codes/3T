package ro.threet.run.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PingController {

	private final PingService pingService;

	PingController(PingService pingService) {
		this.pingService = pingService;
	}

	@GetMapping("/ping")
	public PingResponse ping() {
		return pingService.ping();
	}

}