import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * CS-1 (core loop) — the first release-gate scenario. An anonymous visitor registers for an
 * upcoming run, sees a confirmation, and the confirmation email actually arrives (asserted by
 * querying Mailpit's HTTP API). Runs on desktop and mobile.
 *
 * Prerequisite (local): `docker compose up -d` provides Postgres AND Mailpit; the backend sends
 * to Mailpit on :1025 and this test reads it back on :8025. CI provides both as services.
 */
const MAILPIT = 'http://localhost:8025';

test('CS-1: an anonymous visitor registers and receives a confirmation email', async ({ page, request }) => {
  // Unique per run so re-runs (and the desktop/mobile projects) never hit the duplicate guard.
  const email = `runner-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;

  await page.goto('/');
  await page.getByTestId('event-item').first().getByRole('link', { name: 'Register' }).click();

  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await page.getByLabel('Name').fill('Test Runner');
  await page.getByLabel('Email').fill(email);
  await page.getByTestId('register-submit').click();

  // Confirmation shown to the visitor.
  await expect(page.getByTestId('confirmation')).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();

  // And the email really arrived — poll Mailpit for a message addressed to this run's email.
  await expect(async () => {
    const response = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + email)}`);
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect((body.messages ?? []).length).toBeGreaterThan(0);
  }).toPass({ timeout: 10_000 });
});

test('the registration form has no critical or serious accessibility violations', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('event-item').first().getByRole('link', { name: 'Register' }).click();
  await page.getByTestId('register-submit').waitFor();

  const results = await new AxeBuilder({ page }).analyze();
  const seriousOrWorse = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );

  expect(seriousOrWorse).toEqual([]);
});
