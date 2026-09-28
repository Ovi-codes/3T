package ro.threet.run.registration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A run's leaderboard (#44). Public: it sits under {@code GET /api/events/**}, which
 * {@code SecurityConfig} already opens to anonymous visitors, so anyone can read it. The payload
 * carries only shortened names and times — never an email ({@link PublicName}).
 */
@RestController
@RequestMapping("/api")
public class LeaderboardController {

	private final LeaderboardService leaderboardService;

	LeaderboardController(LeaderboardService leaderboardService) {
		this.leaderboardService = leaderboardService;
	}

	@GetMapping("/events/{id}/leaderboard")
	public LeaderboardResponse leaderboard(@PathVariable Long id) {
		return leaderboardService.leaderboard(id);
	}

}
