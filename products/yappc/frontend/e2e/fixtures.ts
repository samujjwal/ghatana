/**
 * E2E Test Fixtures
 *
 * @description Custom Playwright fixtures for test setup and teardown.
 */

import { test as base, expect, Page } from '@playwright/test';
import { LoginPage, DashboardPage, SprintBoardPage } from './pages';

// =============================================================================
// Test Data Types
// =============================================================================

export interface TestUser {
  email: string;
  password: string;
  name?: string;
}

export interface TestProject {
  id: string;
  name: string;
}

// =============================================================================
// Default Test Data
// =============================================================================

export const testUsers = {
  standard: {
    email: 'test@example.com',
    password: 'TestPassword123!',
    name: 'Test User',
  },
  admin: {
    email: 'admin@example.com',
    password: 'AdminPassword123!',
    name: 'Admin User',
  },
};

// =============================================================================
// Custom Fixtures Type
// =============================================================================

export interface TestFixtures {
  // Pages
  loginPage: LoginPage;
  dashboardPage: DashboardPage;
  sprintBoardPage: SprintBoardPage;
  
  // Auth helpers
  authenticatedPage: Page;
  adminPage: Page;
  
  // Data
  testUser: TestUser;
  testProject: TestProject;
}

// =============================================================================
// Extend Base Test
// =============================================================================

export const test = base.extend<TestFixtures>({
  // Page objects
  loginPage: async ({ page }, use) => {
    const loginPage = new LoginPage(page);
    await use(loginPage);
  },

  dashboardPage: async ({ page }, use) => {
    const dashboardPage = new DashboardPage(page);
    await use(dashboardPage);
  },

  sprintBoardPage: async ({ page }, use) => {
    const sprintBoardPage = new SprintBoardPage(page);
    await use(sprintBoardPage);
  },

  // Pre-authenticated page
  authenticatedPage: async ({ page }, use) => {
    // Login before test
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(testUsers.standard.email, testUsers.standard.password);
    await expect(page).toHaveURL(/dashboard/);
    
    await use(page);
    
    // Cleanup after test (optional)
    // await page.goto('/logout');
  },

  // Admin authenticated page
  adminPage: async ({ page }, use) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(testUsers.admin.email, testUsers.admin.password);
    await expect(page).toHaveURL(/dashboard/);
    
    await use(page);
  },

  // Test user data
  testUser: async ({}, use) => {
    await use(testUsers.standard);
  },

  // Test project (created fresh for each test)
  testProject: async ({ authenticatedPage }, use) => {
    // Create a test project
    const projectName = `Test Project ${Date.now()}`;
    
    // Navigate to create project
    await authenticatedPage.goto('/projects/new');
    await authenticatedPage.getByPlaceholder(/describe/i).fill('E2E test project');
    await authenticatedPage.getByRole('button', { name: /start/i }).click();
    
    // Wait for project creation
    await expect(authenticatedPage).toHaveURL(/projects\/[\w-]+/);
    
    // Extract project ID from URL
    const url = authenticatedPage.url();
    const projectId = url.split('/projects/')[1]?.split('/')[0] || '';
    
    await use({
      id: projectId,
      name: projectName,
    });
    
    // Cleanup: delete the project after test
    // await authenticatedPage.goto(`/projects/${projectId}/settings`);
    // await authenticatedPage.getByRole('button', { name: /delete/i }).click();
    // await authenticatedPage.getByRole('button', { name: /confirm/i }).click();
  },
});

// =============================================================================
// Custom Expect Extensions
// =============================================================================

export { expect } from '@playwright/test';

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Wait for network to be idle
 */
export async function waitForNetworkIdle(page: Page, timeout = 5000) {
  await page.waitForLoadState('networkidle', { timeout });
}

/**
 * Take screenshot with automatic naming
 */
export async function takeScreenshot(page: Page, name: string) {
  await page.screenshot({
    path: `test-results/screenshots/${name}-${Date.now()}.png`,
    fullPage: true,
  });
}

/**
 * Mock API response
 */
export async function mockApiResponse(
  page: Page,
  url: string | RegExp,
  response: unknown,
  status = 200
) {
  await page.route(url, (route) => {
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(response),
    });
  });
}

/**
 * Intercept and delay API response
 */
export async function delayApiResponse(
  page: Page,
  url: string | RegExp,
  delay: number
) {
  await page.route(url, async (route) => {
    await new Promise((resolve) => setTimeout(resolve, delay));
    await route.continue();
  });
}

/**
 * Clear local storage and cookies
 */
export async function clearBrowserState(page: Page) {
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.context().clearCookies();
}

/**
 * Get accessibility violations
 */
export async function getA11yViolations(page: Page) {
  // Would integrate with @axe-core/playwright
  return [];
}
