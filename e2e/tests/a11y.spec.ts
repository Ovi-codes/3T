import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Increment 5 (accessibility pass, issue #30). Two cross-cutting a11y guarantees the per-feature
 * specs don't already cover:
 *
 *  1. The privacy policy — a real, linked page — is axe-clean, completing the "axe across all pages"
 *     sweep alongside events, register, login/signup and the dashboard.
 *  2. The skip-to-content link (WCAG 2.4.1 bypass blocks) is the first thing the keyboard reaches and
 *     jumps focus past the header to the page's <main>.
 *
 * Both are frontend-only, but they run here so the checks live with the rest of the a11y suite and
 * exercise the real rendered DOM. Runs on desktop and mobile.
 */
test('the privacy policy has no critical or serious accessibility violations', async ({ page }) => {
  await page.goto('/privacy');
  await page.getByTestId('privacy').waitFor();

  const results = await new AxeBuilder({ page }).analyze();
  const seriousOrWorse = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );

  expect(seriousOrWorse).toEqual([]);
});

test('the skip link is the first tab stop and jumps focus to the main content', async ({ page }) => {
  await page.goto('/');

  // First Tab from the top of the page lands on the skip link…
  await page.keyboard.press('Tab');
  const skip = page.getByRole('link', { name: 'Skip to content' });
  await expect(skip).toBeFocused();

  // …and activating it moves focus onto the page's <main>, past the header.
  await skip.press('Enter');
  await expect(page.locator('#main-content')).toBeFocused();
});
