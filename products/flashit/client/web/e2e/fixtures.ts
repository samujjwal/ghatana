import { test as base, expect as baseExpect, Page } from '@playwright/test';

/**
 * Test Fixtures and Helpers
 * Common setup for all Playwright tests
 */

interface TestUser {
  email: string;
  password: string;
  name: string;
}

interface Fixtures {
  authenticatedPage: Page;
  testUser: TestUser;
}

export const test = base.extend<Fixtures>({
  testUser: async ({}, use) => {
    const user: TestUser = {
      email: 'test@flashit.app',
      password: 'Test1234!',
      name: 'Test User',
    };
    await use(user);
  },

  authenticatedPage: async ({ page, testUser }, use) => {
    // Login before each test
    await page.goto('/login');
    await page.getByTestId('email-input').fill(testUser.email);
    await page.getByTestId('password-input').fill(testUser.password);
    await page.getByTestId('login-button').click();
    
    // Wait for navigation to home
    await page.waitForURL('/', { timeout: 5000 });
    await page.waitForSelector('[data-testid="home-screen"]', { timeout: 5000 });
    
    await use(page);
    
    // Logout after test
    await page.getByTestId('user-menu').click();
    await page.getByTestId('logout-button').click();
  },
});

export const expect = baseExpect;

/**
 * Helper function to wait for API response
 */
export async function waitForApiResponse(
  page: Page,
  urlPattern: string | RegExp,
  timeout = 5000
) {
  return page.waitForResponse(
    (response) => {
      const url = response.url();
      const matches =
        typeof urlPattern === 'string'
          ? url.includes(urlPattern)
          : urlPattern.test(url);
      return matches && response.status() === 200;
    },
    { timeout }
  );
}

/**
 * Helper to upload file
 */
export async function uploadFile(page: Page, selector: string, filePath: string) {
  const fileChooserPromise = page.waitForEvent('filechooser');
  await page.locator(selector).click();
  const fileChooser = await fileChooserPromise;
  await fileChooser.setFiles(filePath);
}

/**
 * Helper to take screenshot with timestamp
 */
export async function takeScreenshot(page: Page, name: string) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  await page.screenshot({
    path: `screenshots/${name}-${timestamp}.png`,
    fullPage: true,
  });
}

/**
 * Helper to simulate offline mode
 */
export async function setOffline(page: Page) {
  await page.context().setOffline(true);
}

/**
 * Helper to simulate online mode
 */
export async function setOnline(page: Page) {
  await page.context().setOffline(false);
}

/**
 * Helper to clear browser storage
 */
export async function clearStorage(page: Page) {
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
}
