import { test, expect } from '@playwright/test';

/**
 * CS-none (Increment 0 skeleton): proves the browser reaches the backend end to
 * end. Now lives at /ping, since the home page is the events list (Increment 1).
 * Runs under both the desktop and mobile projects defined in the config.
 */
test('the ping page shows the backend status and version', async ({ page }) => {
  await page.goto('/ping');

  await expect(page.getByText('Backend says: ok (v0.0.1)')).toBeVisible();
});