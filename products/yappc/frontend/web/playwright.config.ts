import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for YAPPC web E2E tests.
 *
 * Tests live in e2e/ and run against the local dev server.
 * The dev server is expected to be running on port 3000.
 * Use `pnpm dev` in a separate terminal before running E2E tests.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  workers: process.env['CI'] ? 1 : undefined,
  reporter: [
    ['html', { open: 'never' }],
    ['list'],
  ],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
