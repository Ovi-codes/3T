import { test, expect, type Browser, type Page, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Incr10a (#57): an admin creates an event inline on the events page. Proves the real slice through
 * the live stack — the admin sees the form (a regular account and an anonymous visitor don't), a
 * created run turns up in the upcoming list, and the new UI is axe-clean.
 *
 * Incr10b (#58): and then manages it — editing an upcoming run, deleting one nobody has signed up
 * for, and cancelling one people have (which is the only way out once there are registrations, see
 * ADR-0001). Cancelling is checked end to end: the admin keeps a badged, read-only card, the run
 * drops off the public list, and the person registered sees it called off on their own dashboard.
 *
 * The authorization boundary itself is server-side and covered by the backend integration test
 * (403/401 on /api/admin/**); here we drive the browser the way an admin actually would.
 *
 * Prerequisite (local): `docker compose up -d` for Postgres. The backend is started by Playwright's
 * webServer with ADMIN_EMAILS set (see playwright.config.ts) so the account below is an admin.
 */
const BACKEND = 'http://localhost:8080';
/** Absolute, because a context we open ourselves doesn't inherit the project's baseURL. */
const FRONTEND = 'http://localhost:4200';

/** Must match ADMIN_EMAILS in playwright.config.ts — this account is granted ROLE_ADMIN. */
const ADMIN_EMAIL = 'admin@e2e.threet.ro';
const PASSWORD = 'correct horse battery';

/** Unique per run so re-runs and the desktop/mobile projects never collide on the unique-email rule. */
function uniqueEmail(): string {
  return `user-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

/** A date ~30 days out as YYYY-MM-DD, matching the date input's value format. */
function futureDate(): string {
  return new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

/**
 * Seed the fixed admin account (idempotent across runs and the two viewport projects — a repeat
 * sign-up just fails the unique-email rule, which we ignore because the account already exists), then
 * log in through the UI. The admin email is fixed to match ADMIN_EMAILS, so it can't use uniqueEmail.
 */
async function loginAsAdmin(page: Page, request: APIRequestContext): Promise<void> {
  await request.post(`${BACKEND}/api/auth/signup`, {
    data: { name: 'E2E Admin', email: ADMIN_EMAIL, password: PASSWORD },
  });
  await page.goto('/login');
  await page.getByLabel('Email').fill(ADMIN_EMAIL);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

/** The card for one run, found by its (unique) name — the schedule carries every other run too. */
function runCard(page: Page, name: string) {
  return page.getByTestId('event-item').filter({ hasText: name });
}

/** Create a run through the admin form and wait until the page confirms it. */
async function createRun(page: Page, name: string): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('admin-create')).toBeVisible();
  await page.getByTestId('create-name').fill(name);
  await page.getByTestId('create-date').fill(futureDate());
  await page.getByTestId('create-hour').selectOption('18');
  await page.getByTestId('create-minute').selectOption('30');
  await page.getByTestId('create-submit').click();
  await expect(page.getByTestId('create-success')).toContainText(name);
}

/** The id of a run on the public list, so the API can be driven against it. */
async function eventIdByName(request: APIRequestContext, name: string): Promise<number> {
  const events = (await (await request.get(`${BACKEND}/api/events`)).json()) as {
    id: number;
    name: string;
  }[];
  const match = events.find((event) => event.name === name);
  expect(match, `"${name}" should be on the public list`).toBeTruthy();
  return match!.id;
}

/** Sign a fresh runner up and register them for the given run — the real public path. */
async function registerSomeone(request: APIRequestContext, eventId: number): Promise<string> {
  const email = uniqueEmail();
  await request.post(`${BACKEND}/api/auth/signup`, {
    data: { name: 'Ana Pop', email, password: PASSWORD },
  });
  const registered = await request.post(`${BACKEND}/api/registrations`, {
    data: { eventId, name: 'Ana Pop', email },
  });
  expect(registered.ok()).toBeTruthy();
  return email;
}

/** That runner's own dashboard, in a session of its own — the admin stays signed in on `page`. */
async function dashboardOf(browser: Browser, email: string): Promise<Page> {
  const context = await browser.newContext();
  const runnerPage = await context.newPage();
  await runnerPage.goto(`${FRONTEND}/login`);
  await runnerPage.getByLabel('Email').fill(email);
  await runnerPage.getByLabel('Password').fill(PASSWORD);
  await runnerPage.getByTestId('login-submit').click();
  await expect(runnerPage.getByTestId('dashboard')).toBeVisible();
  return runnerPage;
}

test('an admin creates a run inline and it appears in the upcoming list', async ({ page, request }) => {
  await loginAsAdmin(page, request);

  const name = `E2E Autumn Run ${Date.now()}`;
  await createRun(page, name);

  // The success banner names the run, and it shows up among the upcoming runs.
  await expect(runCard(page, name)).toBeVisible();
});

test('an admin edits a run and the change is what the server now holds', async ({ page, request }) => {
  await loginAsAdmin(page, request);
  const name = `E2E Edit Run ${Date.now()}`;
  await createRun(page, name);

  const card = runCard(page, name);
  await card.getByTestId('event-edit').click();
  const renamed = `${name} moved`;
  await card.getByTestId('edit-name').fill(renamed);
  await card.getByTestId('edit-hour').selectOption('07');
  await card.getByTestId('edit-save').click();

  await expect(runCard(page, renamed)).toBeVisible();

  // Reload: the rename and the new start came back from the server, not from the page's own state.
  // The hour is read back out of the form rather than off the card, so the assertion holds whatever
  // timezone the browser running this is in.
  await page.reload();
  const moved = runCard(page, renamed);
  await moved.getByTestId('event-edit').click();
  await expect(moved.getByTestId('edit-hour')).toHaveValue('07');
});

test('an admin deletes a run nobody has registered for', async ({ page, request }) => {
  await loginAsAdmin(page, request);
  const name = `E2E Delete Run ${Date.now()}`;
  await createRun(page, name);

  const card = runCard(page, name);
  await expect(card).toContainText('0 registered');
  await card.getByTestId('event-remove').click();
  await card.getByTestId('remove-delete').click();

  await expect(runCard(page, name)).toHaveCount(0);
});

test('a run people have signed up for can only be cancelled, and everyone sees it', async ({
  page,
  request,
  browser,
}) => {
  await loginAsAdmin(page, request);
  const name = `E2E Cancel Run ${Date.now()}`;
  await createRun(page, name);

  const runner = await registerSomeone(request, await eventIdByName(request, name));
  await page.reload();

  const card = runCard(page, name);
  await expect(card).toContainText('1 registered');
  await card.getByTestId('event-remove').click();
  // Deleting is off the table — the confirmation says who it would affect and offers cancelling.
  await expect(card.getByTestId('remove-delete')).toHaveCount(0);
  await expect(card.getByTestId('remove-confirm')).toContainText('1 registration');
  await card.getByTestId('remove-cancel-run').click();

  // The admin keeps the run on the schedule, badged and read-only.
  await expect(card.getByTestId('event-cancelled')).toBeVisible();
  await expect(card.getByTestId('event-readonly')).toBeVisible();
  await expect(card.getByTestId('event-edit')).toHaveCount(0);

  // It is gone from the public list…
  const publicEvents = (await (await request.get(`${BACKEND}/api/events`)).json()) as {
    name: string;
  }[];
  expect(publicEvents.some((event) => event.name === name)).toBe(false);

  // …but the person registered still sees it, called off.
  const runnerPage = await dashboardOf(browser, runner);
  const run = runnerPage.getByTestId('upcoming-item').filter({ hasText: name });
  await expect(run.getByTestId('run-cancelled')).toBeVisible();
  await runnerPage.context().close();
});

test('a regular account does not see the create form', async ({ page, request }) => {
  const email = uniqueEmail();
  await request.post(`${BACKEND}/api/auth/signup`, {
    data: { name: 'Ana Pop', email, password: PASSWORD },
  });
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.goto('/');
  await expect(page.getByTestId('event-item').first()).toBeVisible();
  await expect(page.getByTestId('admin-create')).toHaveCount(0);
});

test('an anonymous visitor does not see the create form', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('event-item').first()).toBeVisible();
  await expect(page.getByTestId('admin-create')).toHaveCount(0);
});

/** Critical/serious axe violations on the page as it currently stands. */
async function seriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze();
  return results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );
}

test('the admin create form has no critical or serious accessibility violations', async ({ page, request }) => {
  await loginAsAdmin(page, request);
  await page.goto('/');
  await page.getByTestId('admin-create').waitFor();

  expect(await seriousViolations(page)).toEqual([]);
});

test('the admin edit and remove controls have no critical or serious accessibility violations', async ({
  page,
  request,
}) => {
  await loginAsAdmin(page, request);
  const name = `E2E A11y Run ${Date.now()}`;
  await createRun(page, name);
  const card = runCard(page, name);

  // Each control opens its own state, so axe has to see all three, not just the resting card.
  expect(await seriousViolations(page)).toEqual([]);

  await card.getByTestId('event-edit').click();
  await card.getByTestId('edit-form').waitFor();
  expect(await seriousViolations(page)).toEqual([]);

  await card.getByTestId('edit-dismiss').click();
  await card.getByTestId('event-remove').click();
  await card.getByTestId('remove-confirm').waitFor();
  expect(await seriousViolations(page)).toEqual([]);

  // Leave nothing behind for the other tests' schedules.
  await card.getByTestId('remove-delete').click();
  await expect(runCard(page, name)).toHaveCount(0);
});
