import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E Testing Configuration
 * 
 * @doc.type config
 * @doc.purpose E2E testing configuration for Data Cloud UI
 * @doc.layer testing
 */
export default defineConfig({
  testDir: './e2e',
  
  // Maximum time one test can run
  timeout: 30 * 1000,
  
  // Test execution settings
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  
  // Reporter configuration
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'test-results/results.json' }],
    ['junit', { outputFile: 'test-results/junit.xml' }],
    ['list'],
  ],
  
  // Shared settings for all projects
  use: {
    // Base URL for navigation
    baseURL: process.env.VITE_APP_URL || 'http://localhost:5173',
    
    // Collect trace on first retry
    trace: 'on-first-retry',
    
    // Screenshot on failure
    screenshot: 'only-on-failure',
    
    // Video on failure
    video: 'retain-on-failure',
    
    // API endpoint
    extraHTTPHeaders: {
      'Accept': 'application/json',
    },
  },

  // Configure projects for major browsers
  projects: [
    // ---------------------------------------------------------------------------
    // Auth setup: runs once before the 'authenticated' project to persist login
    // state in e2e/.auth/user.json. Tests that need auth depend on this project.
    // ---------------------------------------------------------------------------
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
      use: { ...devices['Desktop Chrome'] },
    },

    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    // Mobile viewports
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 12'] },
    },
    // ---------------------------------------------------------------------------
    // Authenticated project: reuses the login state persisted by the 'setup'
    // project so individual tests do not repeat the auth flow.
    // ---------------------------------------------------------------------------
    {
      name: 'authenticated',
      use: {
        ...devices['Desktop Chrome'],
        // Reuse the auth state written by e2e/auth.setup.ts
        storageState: 'e2e/.auth/user.json',
      },
      dependencies: ['setup'],
    },
  ],

  // Run local dev server before starting tests
  webServer: [
    {
      // Cross-platform: use the Gradle wrapper script appropriate for the OS.
      // On Linux/macOS CI agents this resolves to `./gradlew`.
      // On Windows developer workstations this resolves to `gradlew.bat`.
      command: process.platform === 'win32'
        ? '.\\gradlew.bat :products:data-cloud:delivery:launcher:runLauncher'
        : './gradlew :products:data-cloud:delivery:launcher:runLauncher',
      // From delivery/ui we need four levels up to repo root (contains gradlew).
      cwd: '../../../..',
      url: 'http://127.0.0.1:8082/health',
      reuseExistingServer: !process.env.CI,
      timeout: 240 * 1000,
      env: {
        ...process.env,
        DATACLOUD_PROFILE: process.env.DATACLOUD_PROFILE ?? 'local',
        DATACLOUD_HTTP_ENABLED: process.env.DATACLOUD_HTTP_ENABLED ?? 'true',
      },
    },
    {
      // Use pnpm directly so Playwright does not depend on corepack being installed.
      command: 'pnpm exec vite --host 127.0.0.1 --port 5173',
      cwd: '.',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 120 * 1000,
      env: {
        ...process.env,
        VITE_USE_MSW: 'false',
      },
    },
  ],
});
