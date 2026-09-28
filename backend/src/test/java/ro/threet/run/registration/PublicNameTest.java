package ro.threet.run.registration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a runner's name appears on the public leaderboard (#44): first name plus the initial of the
 * last name — enough for a runner to find themselves, not enough to fully identify a stranger
 * (GDPR data minimisation, charter §7).
 */
class PublicNameTest {

	@ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
	@CsvSource(delimiter = '|', value = {
			"Ana Pop                | Ana P.",
			"Ana Maria Pop          | Ana P.",
			"Ana-Maria Pop-Ionescu  | Ana-Maria P.",
			"'  Ana    Pop  '       | Ana P.",
			"ana pop                | ana P.",
			"Ștefan Țurcanu         | Ștefan Ț.",
			"Cher                   | Cher",
	})
	void showsTheFirstNameAndTheLastNamesInitial(String fullName, String expected) {
		assertThat(PublicName.of(fullName)).isEqualTo(expected);
	}

}
