import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright Configuration for AI-Native DevSecOps Platform
 * 
 * @see https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  timeout: 60000, // Increased for AI streaming tests
  expect: {
    timeout: 10000, // Increased for WebSocket operations
  },
  // Control whether Playwright serves the HTML report after tests via env flag
  // Set PLAYWRIGHT_SERVE_HTML=1 to serve the report (useful locally). Default is to not serve.
  reporter: [
    [
      'html',
      {
        outputFolder: 'playwright-report',
        // 'open' controls serving/opening the report. Use 'always' when explicitly requested.
        // Valid values: 'always' | 'on-failure' | 'never'
        open: process.env.PLAYWRIGHT_SERVE_HTML === '1' ? 'always' : 'never',
      },
    ],
    ['list'],
    ['json', { outputFile: 'test-results/e2e-results.json' }],
    ['junit', { outputFile: 'test-results/junit.xml' }], // Added for CI/CD
    ...(process.env.CI ? [['github'] as const] : []),
  ],
  use: {
    // Allow overriding baseURL via env (useful when Vite picks a different port)
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173',
    // Load preseeded storage when canvas seeding is enabled
    storageState: process.env.PLAYWRIGHT_ENABLE_CANVAS
      ? './e2e/playwright-storage-state.json'
      : undefined,
    trace:
      process.env.PLAYWRIGHT_ALWAYS_TRACE === '1' ? 'on' : 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10000,
    navigationTimeout: 30000,
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 720 },
      },
    },
    // Uncomment for cross-browser testing
    // {
    //   name: 'firefox',
    //   use: { ...devices['Desktop Firefox'] },
    // },
    // {
    //   name: 'webkit',
    //   use: { ...devices['Desktop Safari'] },
    // },
    // {
    //   name: 'Mobile Chrome',
    //   use: { ...devices['Pixel 5'] },
    // },
    // {
    //   name: 'Mobile Safari',
    //   use: { ...devices['iPhone 12'] },
    // },
  ],
  webServer: {
    command: 'pnpm dev:web',
    url: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
  outputDir: 'test-results/e2e',
  // Global setup: seed demo data for heavy canvas/diagram tests when enabled.
  // Enable by setting PLAYWRIGHT_ENABLE_CANVAS=true in the environment.
  globalSetup: process.env.PLAYWRIGHT_ENABLE_CANVAS
    ? './e2e/global-setup.ts'
    : undefined,
});
