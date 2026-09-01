import { test, expect, APIRequestContext, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { Client } from 'pg';

/**
 * CS-4, CS-5, CS-6 (dashboard) — the last three release-gate scenarios; with these the full CS-1..6
 * suite gates every push. A signed-in user's registration for a future run shows under Upcoming
 * (CS-4) and one for a run whose date has passed shows under Past (CS-5); an anonymous visitor
 * hitting the dashboard is sent to log in (CS-6). Runs on desktop and mobile.
 *
 * Prerequisite (local): `docker compose up -d` for Postgres. The backend is started by Playwright's
 * webServer with SESSION_COOKIE_SECURE=false so the session cookie is stored over http. Past
 * registrations are seeded straight into Postgres — the public registration API refuses past events
 * by design, but "past" here just means the run has since gone by.
 */
const BACKEND = 'http://localhost:8080';
const PG = { host: 'localhost', port: 5432, user: 'runro', password: 'runro', database: 'runro' };

/** Unique per run so re-runs and the desktop/mobile projects never collide on the unique-email rule. */
function uniqueEmail(): string {
  return `dash-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

interface NewUser {
  id: number;
  email: string;
  password: string;
}

/** Create a fresh account through the API and return it (id needed for direct DB seeding). */
async function signUp(request: APIRequestContext): Promise<NewUser> {
  const email = uniqueEmail();
  const password = 'correct horse battery';
  const response = await request.post(`${BACKEND}/api/auth/signup`, { data: { email, password } });
  expect(response.ok()).toBeTruthy();
  const account = (await response.json()) as { id: number };
  return { id: account.id, email, password };
}

async function logIn(page: Page, user: NewUser): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Password').fill(user.password);
  await page.getByTestId('login-submit').click();
  await expect(page.getByTestId('dashboard')).toBeVisible();
}

test('CS-4: a registration for an upcoming run shows under Upcoming', async ({ page, request }) => {
  const user = await signUp(request);

  // Register for the soonest upcoming run through the API, on the account's own session — the real
  // signed-in registration path (Increment 3, Task 3), so the row is attributed to this user.
  const events = await (await request.get(`${BACKEND}/api/events`)).json();
  const soonest = events[0];
  const registered = await request.post(`${BACKEND}/api/registrations`, {
    data: { eventId: soonest.id, name: 'Test Runner', email: user.email },
  });
  expect(registered.ok()).toBeTruthy();

  await logIn(page, user);

  const upcoming = page.getByTestId('section-upcoming');
  const past = page.getByTestId('section-past');
  await expect(upcoming.getByTestId('upcoming-item')).toHaveCount(1);
  await expect(upcoming.getByText('Next')).toBeVisible();
  // Nothing has gone by for this fresh account — Past is empty.
  await expect(past.getByTestId('past-item')).toHaveCount(0);
  await expect(past).toContainText('No past runs yet');
});

test('CS-5: a registration for a run that has passed shows under Past', async ({ page, request }) => {
  const user = await signUp(request);

  const client = new Client(PG);
  await client.connect();
  try {
    const { rows } = await client.query(
      'select id from event where start_datetime < now() order by start_datetime desc limit 1',
    );
    expect(rows.length).toBeGreaterThan(0);
    await client.query(
      'insert into registration (event_id, name, email, user_id) values ($1, $2, $3, $4)',
      [rows[0].id, 'Test Runner', user.email, user.id],
    );
  } finally {
    await client.end();
  }

  await logIn(page, user);

  const upcoming = page.getByTestId('section-upcoming');
  const past = page.getByTestId('section-past');
  await expect(past.getByTestId('past-item')).toHaveCount(1);
  await expect(upcoming.getByTestId('upcoming-item')).toHaveCount(0);
  await expect(upcoming).toContainText('No upcoming runs yet');
});

test('CS-6: an anonymous visitor is sent to log in', async ({ page }) => {
  await page.goto('/dashboard');

  // The guard redirects rather than showing the dashboard — the prompt to sign in.
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-submit')).toBeVisible();
  await expect(page.getByTestId('dashboard')).toHaveCount(0);
});

test('the dashboard has no critical or serious accessibility violations', async ({ page, request }) => {
  const user = await signUp(request);

  // Populate it so axe sees the real card layout, including the amber "Next" badge (its ink-on-amber
  // contrast is exactly the kind of thing this guards).
  const events = await (await request.get(`${BACKEND}/api/events`)).json();
  await request.post(`${BACKEND}/api/registrations`, {
    data: { eventId: events[0].id, name: 'Test Runner', email: user.email },
  });

  await logIn(page, user);
  await page.getByTestId('upcoming-item').first().waitFor();

  const results = await new AxeBuilder({ page }).analyze();
  const seriousOrWorse = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );

  expect(seriousOrWorse).toEqual([]);
});
