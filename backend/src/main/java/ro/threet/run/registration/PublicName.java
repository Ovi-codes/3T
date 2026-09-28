package ro.threet.run.registration;

import java.util.Locale;

/**
 * How a runner's name appears on the public leaderboard (#44): the first name plus the initial of
 * the last name — "Ana Maria Pop" is "Ana P.". Enough for a runner to find themselves, not enough to
 * fully identify a stranger (GDPR data minimisation, charter §7). A single-word name is shown as is.
 */
final class PublicName {

	private PublicName() {
	}

	static String of(String fullName) {
		String[] words = fullName.trim().split("\\s+");
		String first = words[0];
		if (words.length == 1) {
			return first;
		}
		String last = words[words.length - 1];
		// By code point, not char, so an initial outside the BMP isn't cut in half.
		String initial = new String(Character.toChars(last.codePointAt(0))).toUpperCase(Locale.ROOT);
		return first + " " + initial + ".";
	}

}
