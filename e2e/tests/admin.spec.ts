import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Incr10a (#57): an admin creates an event inline on the events page. Proves the real slice through
 * the live stack — the admin sees the form (a regular account and an anonymous visitor don't), a
 * created run turns up in the upcoming list, and the new UI is axe-clean.
 *
 * The authorization boundary itself is server-side and covered by the backend integration test
 * (403/401 on /api/admin/**); here we drive the browser the way an admin actually would.
 *
 * Prerequisite (local): `docker compose up -d` for Postgres. The backend is started by Playwright's
 * webServer with ADMIN_EMAILS set (see playwright.config.ts) so the account below is an admin.
 */
const BACKEND = 'http://localhost:8080';

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

test('an admin creates a run inline and it appears in the upcoming list', async ({ page, request }) => {
  await loginAsAdmin(page, request);
  await page.goto('/');

  await expect(page.getByTestId('admin-create')).toBeVisible();

  const name = `E2E Autumn Run ${Date.now()}`;
  await page.getByTestId('create-name').fill(name);
  await page.getByTestId('create-date').fill(futureDate());
  await page.getByTestId('create-hour').selectOption('18');
  await page.getByTestId('create-minute').selectOption('30');
  await page.getByTestId('create-submit').click();

  // The success banner names the run, and it shows up among the upcoming runs.
  await expect(page.getByTestId('create-success')).toContainText(name);
  await expect(page.getByTestId('event-item').filter({ hasText: name })).toBeVisible();
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

test('the admin create form has no critical or serious accessibility violations', async ({ page, request }) => {
  await loginAsAdmin(page, request);
  await page.goto('/');
  await page.getByTestId('admin-create').waitFor();

  const results = await new AxeBuilder({ page }).analyze();
  const seriousOrWorse = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );

  expect(seriousOrWorse).toEqual([]);
});
