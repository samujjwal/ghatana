/**
 * Playwright configuration for DMOS real-backend release gate E2E tests.
 *
 * P0-006: These tests run against a real API server + real PostgreSQL (via Testcontainers
 * or a pre-provisioned test environment). They do NOT use page.route() mocks.
 *
 * Required environment variables:
 *   DMOS_API_BASE_URL   — base URL of the running DMOS API (e.g., http://localhost:8080)
 *   DMOS_UI_BASE_URL    — base URL of the running DMOS UI (e.g., http://localhost:5174)
 *   DMOS_TEST_TOKEN     — valid JWT for a test user with brand-manager role
 *   DMOS_TEST_WORKSPACE — workspace ID of the test workspace
 *
 * Run:
 *   pnpm test:e2e:realbackend
 *   DMOS_API_BASE_URL=http://localhost:8080 DMOS_UI_BASE_URL=http://localhost:5174 \
 *     DMOS_TEST_TOKEN=<token> DMOS_TEST_WORKSPACE=ws-test pnpm test:e2e:realbackend
 */
import { defineConfig, devices } from '@playwright/test';

const uiBaseUrl = process.env['DMOS_UI_BASE_URL'] ?? 'http://localhost:5174';

export default defineConfig({
  testDir: './e2e-realbackend',
  testMatch: '**/*.spec.ts',
  fullyParallel: false, // Real backend tests are sequential to avoid race conditions
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  workers: 1, // Serial for data isolation
  reporter: process.env['CI'] ? 'github' : 'list',
  timeout: 60_000,

  use: {
    baseURL: uiBaseUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium-realbackend',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // Do NOT start a web server here — caller must start UI + API before running
});
