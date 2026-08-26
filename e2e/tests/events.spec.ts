import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Increment 1 acceptance: an anonymous visitor opens the events page and sees the
 * upcoming runs ordered by date. Runs under both the desktop and mobile projects.
 *
 * The seed uses times relative to "now", so we assert the *ordering* of the machine
 * dates on the <time datetime> attributes rather than any fixed calendar date.
 */
test('upcoming events are listed in date order', async ({ page }) => {
  await page.goto('/');

  const items = page.getByTestId('event-item');
  await expect(items.first()).toBeVisible();
  expect(await items.count()).toBeGreaterThan(1);

  const datetimes = await page
    .locator('[data-testid="event-item"] time')
    .evaluateAll((nodes) => nodes.map((n) => n.getAttribute('datetime') ?? ''));

  const asMillis = datetimes.map((d) => Date.parse(d));
  const ascending = [...asMillis].sort((a, b) => a - b);
  expect(asMillis).toEqual(ascending);
});

test('the events page has no critical or serious accessibility violations', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('event-item').first().waitFor();

  const results = await new AxeBuilder({ page }).analyze();
  const seriousOrWorse = results.violations.filter(
    (v) => v.impact === 'critical' || v.impact === 'serious',
  );

  expect(seriousOrWorse).toEqual([]);
});