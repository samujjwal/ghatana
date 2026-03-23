import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for TutorPutor E2E tests
 */
export default defineConfig({
  testDir: './',
  testMatch: '**/*.spec.ts',
  
  // Global configuration
  timeout: 30000,
  expect: {
    timeout: 5000,
  },
  
  // Retry configuration for CI
  retries: process.env.CI ? 2 : 0,
  
  // Reporter configuration
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'test-results.json' }],
    ['junit', { outputFile: 'test-results.xml' }],
    ['list'],
  ],
  
  // Global setup/teardown
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  
  // Projects configuration
  projects: [
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
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] },
    },
  ],
  
  // Web server configuration
  webServer: {
    command: 'cd ../../apps/tutorputor-web && pnpm dev',
    port: 5173,
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
  
  // Environment variables
  use: {
    // Global test configuration
    baseURL: process.env.BASE_URL || 'http://localhost:5173',
    platformURL: process.env.PLATFORM_URL || 'http://localhost:7105',
    
    // Test data
    testUser: {
      email: process.env.TEST_EMAIL || 'test@example.com',
      password: process.env.TEST_PASSWORD || 'test-password-123',
    },
    
    // API configuration
    apiTimeout: 10000,
    
    // Screenshot configuration
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure',
  },
});
