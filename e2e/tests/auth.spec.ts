import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * CS-2 and CS-3 (accounts) — the second and third release-gate scenarios. Sign-up creates an
 * account and lands on the dashboard; an existing account logs in and reaches it too. The dashboard
 * is a stub this increment, so the assertion is *arrival at the route*. Runs on desktop and mobile.
 *
 * Prerequisite (local): `docker compose up -d` for Postgres. The backend is started by Playwright's
 * webServer with SESSION_COOKIE_SECURE=false so the session cookie is stored over http.
 */
const BACKEND = 'http://localhost:8080';

/** Unique per run so re-runs and the desktop/mobile projects never collide on the unique-email rule. */
function uniqueEmail(): string {
  return `user-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

test('CS-2: signing up creates an account, lands on the dashboard, and signing out returns to login', async ({
  page,
}) => {
  const email = uniqueEmail();

  await page.goto('/signup');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('correct horse battery');
  await page.getByTestId('signup-submit').click();

  // Arrived at the dashboard, signed in.
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByTestId('dashboard')).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();

  // Sign out returns to login…
  await page.getByTestId('logout').click();
  await expect(page).toHaveURL(/\/login$/);

  // …and the guard now keeps the dashboard closed to the signed-out visitor.
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});

test('CS-3: an existing account logs in and reaches the dashboard', async ({ page, request }) => {
  const email = uniqueEmail();
  const password = 'correct horse battery';

  // Seed the account straight through the API (its own session; the browser logs in fresh below).
  const created = await request.post(`${BACKEND}/api/auth/signup`, { data: { email, password } });
  expect(created.ok()).toBeTruthy();

  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByTestId('login-submit').click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByTestId('dashboard')).toBeVisible();
});

test('wrong credentials show an error and stay on the login page', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('nobody@example.com');
  await page.getByLabel('Password').fill('definitely wrong');
  await page.getByTestId('login-submit').click();

  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect.');
  await expect(page).toHaveURL(/\/login$/);
});

for (const path of ['/signup', '/login']) {
  test(`the ${path} form has no critical or serious accessibility violations`, async ({ page }) => {
    await page.goto(path);
    await page.getByRole('button').first().waitFor();

    const results = await new AxeBuilder({ page }).analyze();
    const seriousOrWorse = results.violations.filter(
      (violation) => violation.impact === 'critical' || violation.impact === 'serious',
    );

    expect(seriousOrWorse).toEqual([]);
  });
}
