import { test, expect, APIRequestContext, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { Client } from 'pg';

/**
 * Increment 12 (#44) — a past run's public leaderboard. A signed-in runner opens it from a past run
 * on their dashboard (CS-5's past-run view); anyone can open it by link, no session needed. Runs on
 * desktop and mobile.
 *
 * Each test seeds its own past run straight into Postgres — the public registration API refuses past
 * runs by design. The run is dated well before the migration's seeded past runs, so the "most recent
 * past run" that dashboard.spec.ts registers its runners on is never this one, and the field here is
 * exactly what the test put in. It's removed again afterwards.
 */
const BACKEND = 'http://localhost:8080';
const PG = { host: 'localhost', port: 5432, user: 'runro', password: 'runro', database: 'runro' };

/** Unique per run so re-runs and the desktop/mobile projects never collide on the unique-email rule. */
function uniqueEmail(): string {
  return `board-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
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

interface Runner {
  name: string;
  finishTimeSeconds: number | null;
  userId?: number;
}

/**
 * The field, in registration order: a podium, a tie for 3rd, the signed-in runner mid-pack, and a
 * runner who never entered a time — registered early, so only the ranking (not insert order) puts
 * them last.
 */
function field(user?: NewUser): Runner[] {
  return [
    { name: 'Vlad Iordache', finishTimeSeconds: null },
    { name: 'Mihai Constantin', finishTimeSeconds: 1270 },
    { name: 'Andrei Popescu', finishTimeSeconds: 1122 },
    { name: 'Elena Dumitrescu', finishTimeSeconds: 1270 },
    { name: 'Ioana Marinescu', finishTimeSeconds: 1195 },
    { name: 'Ana Pop', finishTimeSeconds: 1417, userId: user?.id },
  ];
}

/** Rows as the page ranks them: [position, runner, time]. */
const EXPECTED_ROWS = [
  ['1', 'Andrei P.', '18:42'],
  ['2', 'Ioana M.', '19:55'],
  ['3', 'Mihai C.', '21:10'],
  ['3', 'Elena D.', '21:10'],
  ['5', 'Ana P.', '23:37'],
];

const seeded: number[] = [];

/** Create a past run with the given runners and return its id. */
async function seedPastRun(name: string, runners: Runner[]): Promise<number> {
  const client = new Client(PG);
  await client.connect();
  try {
    const { rows } = await client.query(
      'insert into event (location_id, name, start_datetime) ' +
        "select id, $1, now() - interval '400 days' from location order by id limit 1 returning id",
      [name],
    );
    const eventId = rows[0].id as number;
    for (const [index, runner] of runners.entries()) {
      await client.query(
        'insert into registration (event_id, name, email, user_id, finish_time) ' +
          "values ($1, $2, $3, $4, $5 * interval '1 second')",
        [eventId, runner.name, `runner-${index}-${uniqueEmail()}`, runner.userId ?? null, runner.finishTimeSeconds],
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

/** Every row's cells as text, in page order (the medal column is checked on its own). */
async function rowsOf(page: Page): Promise<string[][]> {
  const rows = page.getByTestId('leaderboard-row');
  await expect(rows.first()).toBeVisible();
  return rows.evaluateAll((trs) =>
    trs.map((tr) => Array.from(tr.querySelectorAll('th, td:not(.col-medal)')).map((cell) => (cell as HTMLElement).innerText.trim())),
  );
}

test('CS-5: a runner opens a past run’s results from their dashboard', async ({ page, request }) => {
  const user = await signUp(request);
  const runName = `Results run ${Date.now()}`;
  const eventId = await seedPastRun(runName, field(user));

  await logIn(page, user);
  const pastRun = page.getByTestId('section-past').getByTestId('past-item');
  await expect(pastRun).toHaveCount(1);
  await pastRun.getByTestId('leaderboard-link').click();

  await expect(page).toHaveURL(new RegExp(`/leaderboard/${eventId}$`));
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(runName);

  const rows = await rowsOf(page);
  // Fastest first; the tie for 3rd shares its place and the next runner skips to 5th.
  expect(rows.slice(0, 5)).toEqual(EXPECTED_ROWS);
  // The runner who never entered a time comes last, with no position.
  expect(rows[5][1]).toBe('Vlad I.');
  expect(rows[5][2]).toBe('Time not entered');
  expect(rows[5][0]).not.toMatch(/\d/);

  // Only first name + last initial ever shows — no full surname on the public page.
  await expect(page.getByTestId('leaderboard')).not.toContainText('Popescu');
});

test('anyone can open a past run’s results, no sign-in needed', async ({ page }) => {
  const eventId = await seedPastRun(`Public results ${Date.now()}`, field());

  await page.goto(`/leaderboard/${eventId}`);

  expect((await rowsOf(page)).slice(0, 5)).toEqual(EXPECTED_ROWS);
  await expect(page.getByTestId('leaderboard-summary')).toHaveText('5 of 6 runners have entered a time.');
  // The podium gets medals; the tie for 3rd means two bronzes.
  const board = page.getByTestId('leaderboard');
  await expect(board.getByRole('img', { name: 'Gold medal' })).toHaveCount(1);
  await expect(board.getByRole('img', { name: 'Silver medal' })).toHaveCount(1);
  await expect(board.getByRole('img', { name: 'Bronze medal' })).toHaveCount(2);
});

test('an upcoming run has no results yet', async ({ page, request }) => {
  const events = await (await request.get(`${BACKEND}/api/events`)).json();

  await page.goto(`/leaderboard/${events[0].id}`);

  await expect(page.getByTestId('leaderboard-unavailable')).toContainText('taken place yet');
  await expect(page.getByTestId('leaderboard-row')).toHaveCount(0);
});

test('the leaderboard has no critical or serious accessibility violations', async ({ page }) => {
  const eventId = await seedPastRun(`A11y results ${Date.now()}`, field());

  await page.goto(`/leaderboard/${eventId}`);
  await page.getByTestId('leaderboard-row').first().waitFor();

  const results = await new AxeBuilder({ page }).analyze();
  const seriousOrWorse = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );

  expect(seriousOrWorse).toEqual([]);
});
