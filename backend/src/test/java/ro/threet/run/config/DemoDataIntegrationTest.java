package ro.threet.run.config;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ro.threet.run.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The opt-in demo seed (#44): with {@code DEMO_DATA=true} a past run comes pre-filled with runners
 * and finish times, plus a demo account that ran it, so the leaderboard can be shown without typing
 * data in. Without the flag nothing is seeded — tests and prod never see the fake runners.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DemoDataIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void isOffByDefault() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where script like '%demo%'", Integer.class);
		assertThat(applied).isZero();
	}

	@Nested
	@TestPropertySource(properties = "app.demo-data=true")
	class WhenEnabled {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void seedsAPastRunWithARankedLeaderboardTheDemoAccountRan() throws Exception {
			MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"email": "demo@example.com", "password": "demo-run-5k"}"""))
					.andExpect(status().isOk())
					.andReturn();
			MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

			// The demo account's dashboard lists the seeded run under Past, with its recorded time.
			String dashboard = mockMvc.perform(get("/api/me/registrations").session(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.past.length()").value(1))
					.andExpect(jsonPath("$.past[0].finishTimeSeconds").value(1417))
					.andReturn().getResponse().getContentAsString();
			long eventId = ((Number) JsonPath.read(dashboard, "$.past[0].eventId")).longValue();

			// Its leaderboard shows every shape the view has to handle: a podium, a tie, the demo
			// runner mid-pack, and runners who never entered a time.
			mockMvc.perform(get("/api/events/" + eventId + "/leaderboard"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.entries.length()").value(11))
					.andExpect(jsonPath("$.entries[0].position").value(1))
					.andExpect(jsonPath("$.entries[0].runnerName").value("Andrei P."))
					.andExpect(jsonPath("$.entries[2].position").value(3))
					.andExpect(jsonPath("$.entries[3].position").value(3))
					.andExpect(jsonPath("$.entries[4].position").value(5))
					.andExpect(jsonPath("$.entries[4].runnerName").value("Demo R."))
					.andExpect(jsonPath("$.entries[9].finishTimeSeconds").value(nullValue()))
					.andExpect(jsonPath("$.entries[10].finishTimeSeconds").value(nullValue()));
		}

	}

}
