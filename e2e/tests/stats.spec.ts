import { test, expect, APIRequestContext, Page } from '@playwright/test';
import { csrfHeaders } from './support/csrf';
import AxeBuilder from '@axe-core/playwright';
import { Client } from 'pg';

/**
 * Increment 13 (#45) — a signed-in runner's personal stats on their dashboard: headline numbers, a
 * time-trend chart, and a screen-reader table of time + place per run. Runs on desktop and mobile.
 *
 * Each test seeds its own past runs straight into Postgres (the public registration API refuses past
 * runs by design), dated well before the migration's seeded runs so no other spec's runners land on
 * them. They're removed again afterwards.
 */
const BACKEND = 'http://localhost:8080';
const PG = { host: 'localhost', port: 5432, user: 'runro', password: 'runro', database: 'runro' };

function uniqueEmail(): string {
  return `stats-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

interface NewUser {
  id: number;
  email: string;
  password: string;
}

async function signUp(request: APIRequestContext): Promise<NewUser> {
  const email = uniqueEmail();
  const password = 'correct horse battery';
  const response = await request.post(`${BACKEND}/api/auth/signup`, {
    headers: await csrfHeaders(request),
    data: { name: 'Ana Pop', email, password },
  });
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

const seeded: number[] = [];

/**
 * A past run `daysAgo` days back: the signed-in runner on `mine`, plus anonymous runners on `others`.
 */
async function seedPastRun(
  name: string,
  daysAgo: number,
  user: NewUser,
  mine: number,
  others: number[],
): Promise<number> {
  const client = new Client(PG);
  await client.connect();
  try {
    const { rows } = await client.query(
      'insert into event (location_id, name, start_datetime) ' +
        "select id, $1, now() - $2 * interval '1 day' from location order by id limit 1 returning id",
      [name, daysAgo],
    );
    const eventId = rows[0].id as number;
    const runners = [
      { userId: user.id as number | null, time: mine },
      ...others.map((time) => ({ userId: null, time })),
    ];
    for (const [index, runner] of runners.entries()) {
      await client.query(
        'insert into registration (event_id, name, email, user_id, finish_time) ' +
          "values ($1, $2, $3, $4, $5 * interval '1 second')",
        [
          eventId,
          index === 0 ? 'Ana Pop' : `Runner ${index}`,
          `runner-${index}-${uniqueEmail()}`,
          runner.userId,
          runner.time,
        ],
      );
    }
    seeded.push(eventId);
    return eventId;
  } finally {
    await client.end();
  }
}

test.afterEach(async () => {
  const client = new Client(PG);
  await client.connect();
  try {
    for (const eventId of seeded.splice(0)) {
      await client.query('delete from registration where event_id = $1', [eventId]);
      await client.query('delete from event where id = $1', [eventId]);
    }
  } finally {
    await client.end();
  }
});

/** Two finished runs: 25:00 in 4th of 4, then a best of 23:37 in 2nd of 3. */
async function seedTwoRuns(user: NewUser): Promise<void> {
  await seedPastRun(`Stats older ${Date.now()}`, 410, user, 1500, [1122, 1300, 1450]);
  await seedPastRun(`Stats newer ${Date.now()}`, 403, user, 1417, [1300, 1610]);
}

test('a runner sees their place on each run and their time trend', async ({ page, request }) => {
  const user = await signUp(request);
  await seedTwoRuns(user);

  await logIn(page, user);
  const stats = page.getByTestId('section-stats');

  await expect(stats.getByTestId('stat-runs')).toContainText('2');
  await expect(stats.getByTestId('stat-best-time')).toContainText('23:37');
  await expect(stats.getByTestId('stat-best-place')).toContainText('2nd');

  await expect(stats.getByRole('img', { name: /2 runs/ })).toBeVisible();
  await expect(stats.getByTestId('trend-point')).toHaveCount(2);

  // The table: newest first, time and place on each.
  const rows = stats.getByTestId('stats-table').locator('tbody tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(0)).toContainText('23:37');
  await expect(rows.nth(0)).toContainText('2nd of 3');
  await expect(rows.nth(1)).toContainText('25:00');
  await expect(rows.nth(1)).toContainText('4th of 4');
});

test('hovering — or, on touch, tapping — the chart shows that run’s time and place', async ({
  page,
  request,
  isMobile,
}) => {
  const user = await signUp(request);
  await seedTwoRuns(user);

  await logIn(page, user);
  const chart = page.getByTestId('section-stats').locator('svg[role="img"]');
  // The stats sit below the runs; bring the chart on screen so the pointer can reach it.
  await chart.scrollIntoViewIfNeeded();
  const box = (await chart.boundingBox())!;
  // Near the right edge, so it snaps to the latest run.
  const position = { x: box.width - 4, y: box.height / 2 };
  if (isMobile) {
    await chart.tap({ position });
  } else {
    await page.mouse.move(box.x + position.x, box.y + position.y);
  }

  const tooltip = page.getByTestId('trend-tooltip');
  await expect(tooltip).toBeVisible();
  await expect(tooltip).toContainText('23:37');
  await expect(tooltip).toContainText('2nd of 3');
});

test('a runner with no finished runs is invited to add a time', async ({ page, request }) => {
  const user = await signUp(request);

  await logIn(page, user);

  await expect(page.getByTestId('stats-empty')).toContainText('No stats yet');
  await expect(page.getByTestId('section-stats').locator('svg[role="img"]')).toHaveCount(0);
});

test('the stats have no critical or serious accessibility violations', async ({
  page,
  request,
}) => {
  const user = await signUp(request);
  await seedTwoRuns(user);

  await logIn(page, user);
  await page.getByTestId('trend-point').first().waitFor();

  const results = await new AxeBuilder({ page }).include('[data-testid="section-stats"]').analyze();
  const seriousOrWorse = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );

  expect(seriousOrWorse).toEqual([]);
});
