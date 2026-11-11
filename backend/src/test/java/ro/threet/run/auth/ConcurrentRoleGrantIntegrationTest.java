package ro.threet.run.auth;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import ro.threet.run.TestcontainersConfiguration;
import ro.threet.run.email.EmailSender;
import ro.threet.run.registration.RegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two logins of the same account at the same moment (issue #64), on real Postgres. Each
 * test loads the account in one transaction, then lets a second transaction (the "other
 * login") change its {@code user_roles} rows and commit first. The first login must still
 * succeed, with the right roles in the principal and in the table.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.admin.emails=" + ConcurrentRoleGrantIntegrationTest.ADMIN)
class ConcurrentRoleGrantIntegrationTest {

	static final String ADMIN = "boss@example.com";

	private static final String DEMOTED = "former-boss@example.com";

	@Autowired
	private RoleService roleService;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private RegistrationRepository registrations;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// This slice sends no email; stub it so the context needs no SMTP server.
	@MockitoBean
	private EmailSender emailSender;

	@BeforeEach
	void reset() {
		registrations.deleteAll(); // FK to app_user — clear children first
		jdbc.update("delete from user_roles");
		jdbc.update("delete from app_user");
	}

	@Test
	void aFirstAdminLoginSucceedsWhenAConcurrentLoginGrantedTheRolesFirst() {
		createAccount(ADMIN);

		Set<String> granted = login(ADMIN, () -> {
			grantDirectly(ADMIN, Roles.ROLE_USER);
			grantDirectly(ADMIN, Roles.ROLE_ADMIN);
		});

		assertThat(granted).containsExactlyInAnyOrder(Roles.ROLE_ADMIN, Roles.ROLE_USER);
		assertThat(roleRows(ADMIN)).containsExactlyInAnyOrder(Roles.ROLE_ADMIN, Roles.ROLE_USER);
	}

	@Test
	void aDemotingLoginSucceedsWhenAConcurrentLoginRevokedTheRoleFirst() {
		createAccount(DEMOTED);
		grantDirectly(DEMOTED, Roles.ROLE_USER);
		grantDirectly(DEMOTED, Roles.ROLE_ADMIN);

		Set<String> granted = login(DEMOTED,
				() -> jdbc.update("delete from user_roles where user_id = (select id from app_user where email = ?)"
						+ " and role_id = (select id from role where name = ?)", DEMOTED, Roles.ROLE_ADMIN));

		assertThat(granted).containsExactly(Roles.ROLE_USER);
		assertThat(roleRows(DEMOTED)).containsExactly(Roles.ROLE_USER);
	}

	/**
	 * A login, as {@link LocalAuthProvider#login} does it: load the account, then
	 * reconcile its roles. {@code otherLogin} runs and commits in its own transaction
	 * between the two steps.
	 */
	private Set<String> login(String email, Runnable otherLogin) {
		TransactionTemplate outer = new TransactionTemplate(transactionManager);
		TransactionTemplate inner = new TransactionTemplate(transactionManager);
		inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return outer.execute(status -> {
			AppUser user = users.findByEmail(email).orElseThrow();
			inner.executeWithoutResult(innerStatus -> otherLogin.run());
			return roleService.ensureRolesFor(user);
		});
	}

	private void createAccount(String email) {
		jdbc.update("insert into app_user (email, name, password_hash) values (?, 'Boss', 'hash')", email);
	}

	private void grantDirectly(String email, String roleName) {
		jdbc.update(
				"insert into user_roles (user_id, role_id)"
						+ " select u.id, r.id from app_user u, role r where u.email = ? and r.name = ?",
				email, roleName);
	}

	private java.util.List<String> roleRows(String email) {
		return jdbc.queryForList("select r.name from user_roles ur join role r on r.id = ur.role_id"
				+ " join app_user u on u.id = ur.user_id where u.email = ?", String.class, email);
	}

}
