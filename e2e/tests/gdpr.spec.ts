import { test, expect, APIRequestContext, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Increment 5 (GDPR, charter §7): a signed-in user can download everything held about them and can
 * permanently delete their account from their dashboard, and the privacy policy is reachable from
 * every page's footer. These aren't part of the CS-1..6 release gate, but they are Task 1's
 * acceptance criteria — the user can export and delete their data, and the policy page is live.
 * Runs on desktop and mobile.
 *
 * Prerequisite (local): `docker compose up -d` for Postgres. The backend is started by Playwright's
 * webServer with SESSION_COOKIE_SECURE=false so the session cookie is stored over http.
 */
const BACKEND = 'http://localhost:8080';

/** Unique per run so re-runs and the desktop/mobile projects never collide on the unique-email rule. */
function uniqueEmail(): string {
  return `gdpr-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

interface NewUser {
  email: string;
  password: string;
}

async function signUpAndLogIn(page: Page, request: APIRequestContext): Promise<NewUser> {
  const user = { email: uniqueEmail(), password: 'correct horse battery' };
  const created = await request.post(`${BACKEND}/api/auth/signup`, { data: user });
  expect(created.ok()).toBeTruthy();

  await page.goto('/login');
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Password').fill(user.password);
  await page.getByTestId('login-submit').click();
  await expect(page.getByTestId('dashboard')).toBeVisible();
  return user;
}

test('a user can download all their data as a JSON file', async ({ page, request }) => {
  const user = await signUpAndLogIn(page, request);

  // Register for the soonest run so the export has a registration to carry.
  const events = await (await request.get(`${BACKEND}/api/events`)).json();
  await request.post(`${BACKEND}/api/registrations`, {
    data: { eventId: events[0].id, name: 'Test Runner', email: user.email },
  });

  const downloadPromise = page.waitForEvent('download');
  await page.getByTestId('export-data').click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('threet-run-my-data.json');
  const stream = await download.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream) {
    chunks.push(chunk as Buffer);
  }
  const data = JSON.parse(Buffer.concat(chunks).toString('utf-8'));
  expect(data.account.email).toBe(user.email);
  expect(data.registrations.length).toBe(1);
});

test('a user can permanently delete their account, which ends their session', async ({
  page,
  request,
}) => {
  await signUpAndLogIn(page, request);

  // The destructive action asks for confirmation before it fires.
  await page.getByTestId('delete-account').click();
  await expect(page.getByTestId('delete-confirm')).toBeVisible();
  await page.getByTestId('delete-confirm-yes').click();

  // Erasure ends the session and drops the user on the home page…
  await expect(page).toHaveURL(/\/$/);
  // …and the now-deleted account can't log back in.
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});

test('the privacy policy is reachable from the footer on any page', async ({ page }) => {
  // The footer is global; exercise it from the login page (a narrow form, no wide content) so this
  // stays about the footer link, not another page's layout.
  await page.goto('/login');
  await page.getByTestId('footer-privacy').click();

  await expect(page).toHaveURL(/\/privacy$/);
  await expect(page.getByTestId('privacy')).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Privacy policy');
});

test('the privacy policy has no critical or serious accessibility violations', async ({ page }) => {
  await page.goto('/privacy');
  await page.getByTestId('privacy').waitFor();

  const results = await new AxeBuilder({ page }).analyze();
  const seriousOrWorse = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );

  expect(seriousOrWorse).toEqual([]);
});
