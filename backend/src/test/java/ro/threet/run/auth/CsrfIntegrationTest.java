package ro.threet.run.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.Arrays;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.email.EmailSender;
import ro.threet.run.event.EventRepository;
import ro.threet.run.registration.RegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CSRF token layer, proven through the real filter chain the way a browser uses it:
 * no {@code csrf()} test shortcut here. The first response sets a JS-readable
 * {@code XSRF-TOKEN} cookie; a write that echoes it in {@code X-XSRF-TOKEN} goes through,
 * and one without it (or with the wrong value) is a 403 that changes nothing. Login and
 * logout each swap the token for a fresh one. The confirmation email is stubbed.
 *
 * Runs on a fresh context: spring-security-test's {@code csrf()} post-processor (used by
 * the other integration tests) swaps the cached context's token repository for a
 * session-backed test one, which would hide the real cookie behaviour tested here. It
 * also starts a real server, because one check needs Tomcat's error dispatch, which
 * MockMvc skips.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
class CsrfIntegrationTest {

	private static final String COOKIE = "XSRF-TOKEN";

	private static final String HEADER = "X-XSRF-TOKEN";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private EventRepository events;

	@MockitoBean
	private EmailSender emailSender;

	@LocalServerPort
	private int port;

	@BeforeEach
	void reset() {
		registrations.deleteAll();
		users.deleteAll();
	}

	@Test
	void theFirstResponseSetsAReadableTokenCookie() throws Exception {
		Cookie token = tokenCookie(mockMvc.perform(get("/api/events")).andReturn().getResponse());

		assertThat(token.getValue()).isNotBlank();
		assertThat(token.isHttpOnly()).as("the SPA must be able to read it").isFalse();
		assertThat(token.getPath()).isEqualTo("/");
		assertThat(token.getAttribute("SameSite")).isEqualTo("Lax");
	}

	@Test
	void theTokenEndpointIsPublicAndSetsTheCookie() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse();

		assertThat(tokenCookie(response).getValue()).isNotBlank();
	}

	@Test
	void aRegistrationWithoutATokenIsForbiddenAndSavesNothing() throws Exception {
		mockMvc.perform(registration("ana@example.com")).andExpect(status().isForbidden());

		assertThat(registrations.count()).isZero();
	}

	/**
	 * Over a real server, the CSRF 403 goes through Tomcat's error dispatch. If
	 * deny-by-default covered that dispatch, an anonymous caller would see a misleading
	 * 401 instead.
	 */
	@Test
	void anAnonymousWriteWithoutATokenIsA403OverTheWireNotA401() throws Exception {
		HttpResponse<Void> response = send(HttpRequest.newBuilder(serverUri("/api/registrations"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{}")));

		assertThat(response.statusCode()).isEqualTo(403);
	}

	/**
	 * What the privacy page promises: an anonymous visitor gets the token cookie and
	 * nothing else. A session (JSESSIONID) starts only at sign-in, even when the visitor
	 * hits a protected endpoint and gets a 401.
	 */
	@Test
	void anAnonymousVisitorGetsOnlyTheTokenCookieAndNoSession() throws Exception {
		HttpResponse<Void> response = send(HttpRequest.newBuilder(serverUri("/api/auth/me")).GET());

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().allValues("Set-Cookie")).singleElement().asString().startsWith(COOKIE + "=");
	}

	@Test
	void aRegistrationWithAWrongTokenIsForbidden() throws Exception {
		Cookie token = freshToken();

		mockMvc.perform(registration("ana@example.com").cookie(token).header(HEADER, "not-the-token"))
			.andExpect(status().isForbidden());

		assertThat(registrations.count()).isZero();
	}

	@Test
	void aRegistrationThatEchoesTheCookieInTheHeaderGoesThrough() throws Exception {
		Cookie token = freshToken();

		mockMvc.perform(registration("ana@example.com").cookie(token).header(HEADER, token.getValue()))
			.andExpect(status().isCreated());

		assertThat(registrations.count()).isEqualTo(1);
	}

	@Test
	void loginReplacesTheTokenWithAFreshOne() throws Exception {
		Cookie before = freshToken();
		mockMvc.perform(withToken(post("/api/auth/signup"), before).content("""
				{"name": "Ana Pop", "email": "ana@example.com", "password": "correct horse"}"""))
			.andExpect(status().isCreated());

		MockHttpServletResponse login = mockMvc.perform(withToken(post("/api/auth/login"), before).content("""
				{"email": "ana@example.com", "password": "correct horse"}"""))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();

		Cookie after = tokenCookie(login);
		assertThat(after.getValue()).isNotBlank().isNotEqualTo(before.getValue());
	}

	@Test
	void logoutReplacesTheTokenSoSigningStraightBackInWorks() throws Exception {
		Cookie before = freshToken();
		mockMvc.perform(withToken(post("/api/auth/signup"), before).content("""
				{"name": "Ana Pop", "email": "ana@example.com", "password": "correct horse"}"""))
			.andExpect(status().isCreated());

		Cookie after = tokenCookie(mockMvc.perform(withToken(post("/api/auth/logout"), before))
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse());
		assertThat(after.getValue()).isNotBlank().isNotEqualTo(before.getValue());

		mockMvc.perform(withToken(post("/api/auth/login"), after).content("""
				{"email": "ana@example.com", "password": "correct horse"}""")).andExpect(status().isOk());
	}

	private URI serverUri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	/** HTTP/1.1: the default h2c upgrade attempt on a plain-http request can stall. */
	private static HttpResponse<Void> send(HttpRequest.Builder request) throws Exception {
		return HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.build()
			.send(request.build(), HttpResponse.BodyHandlers.discarding());
	}

	/** What a fresh visitor's browser holds after the first page load. */
	private Cookie freshToken() throws Exception {
		return tokenCookie(mockMvc.perform(get("/api/events")).andReturn().getResponse());
	}

	private MockHttpServletRequestBuilder registration(String email) {
		long eventId = events.findByStartDateTimeGreaterThanEqualOrderByStartDateTimeAsc(OffsetDateTime.now())
			.get(0)
			.getId();
		return post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content("""
				{"eventId": %d, "name": "Ana Pop", "email": "%s"}""".formatted(eventId, email));
	}

	private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, Cookie token) {
		return request.contentType(MediaType.APPLICATION_JSON).cookie(token).header(HEADER, token.getValue());
	}

	/**
	 * The token cookie a browser would keep: the last {@code Set-Cookie} for it wins
	 * (login sends a delete and then the new value).
	 */
	private static Cookie tokenCookie(MockHttpServletResponse response) {
		return Arrays.stream(response.getCookies())
			.filter(cookie -> COOKIE.equals(cookie.getName()))
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("no " + COOKIE + " cookie on the response"));
	}

}
