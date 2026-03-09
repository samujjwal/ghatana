import { defineConfig, devices } from '@playwright/test';

// Local Playwright config for running tests against an already-running dev server
export default defineConfig({
  testDir: './',
  timeout: 30000,
  expect: { timeout: 5000 },
  reporter: [ ['list'], ['json', { outputFile: '../test-results/e2e-results-local.json' }] ],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10000,
    navigationTimeout: 30000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 720 } } },
  ],
  // Intentionally disable webServer so we don't start a second dev server when one is already running
  webServer: undefined,
  outputDir: 'test-results/e2e-local',
});
