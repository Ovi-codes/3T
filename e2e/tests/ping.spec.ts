import { test, expect } from '@playwright/test';

/**
 * CS-none (Increment 0 skeleton): proves the browser reaches the backend end to
 * end. Runs under both the desktop and mobile projects defined in the config.
 */
test('home page shows the backend status and version', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByText('Backend says: ok (v0.0.1)')).toBeVisible();
});