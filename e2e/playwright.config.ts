import { defineConfig, devices } from '@playwright/test';

/**
 * Increment 0 end-to-end check: drive a real browser against the live stack
 * (Angular dev server → /api proxy → Spring Boot → Postgres) and assert the
 * walking-skeleton text renders. Runs on a desktop and a mobile viewport.
 *
 * Prerequisite: `docker compose up -d` (Postgres) must be running — Playwright
 * starts the backend and frontend below, but not the database container.
 */

const FRONTEND_URL = 'http://localhost:4200';
const BACKEND_PING = 'http://localhost:8080/api/ping';

// The Maven wrapper is invoked differently per OS; CI (Linux) uses ./mvnw.
const mvnw =
  process.platform === 'win32' ? '.\\mvnw.cmd spring-boot:run' : './mvnw spring-boot:run';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: 'html',

  use: {
    baseURL: FRONTEND_URL,
    trace: 'on-first-retry',
    // Capture evidence only when a test fails, so green runs stay artifact-free.
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'desktop-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'mobile-chromium',
      use: { ...devices['Pixel 5'] },
    },
  ],

  // Bring the whole stack up for the run. Locally we reuse an already-running
  // server for fast iteration; CI always starts clean.
  webServer: [
    {
      command: mvnw,
      cwd: '../backend',
      url: BACKEND_PING,
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm start',
      cwd: '../frontend',
      url: FRONTEND_URL,
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
});