package ro.threet.run.registration;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ro.threet.run.auth.AccountPrincipal;

/**
 * The current user's personal stats (#45). Requires a session: it is not enumerated in
 * {@code SecurityConfig}, so the deny-by-default rule closes it and an anonymous request gets a
 * plain 401. Because it is authenticated, {@code principal} is always present.
 */
@RestController
@RequestMapping("/api")
public class MyStatsController {

	private final MyStatsService myStatsService;

	MyStatsController(MyStatsService myStatsService) {
		this.myStatsService = myStatsService;
	}

	@GetMapping("/me/stats")
	public MyStatsResponse myStats(@AuthenticationPrincipal AccountPrincipal principal) {
		return myStatsService.stats(principal.id());
	}

}
