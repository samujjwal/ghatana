import { Page, BrowserContext } from '@playwright/test';

/**
 * Test utility functions for E2E tests.
 *
 * @doc.type utility
 * @doc.purpose Common test helpers for E2E testing
 * @doc.layer e2e-testing
 */

/**
 * Clears browser storage safely.
 *
 * @param page - Playwright page object
 * @param context - Playwright browser context
 */
export async function clearStorage(page: Page, context: BrowserContext) {
  // Clear cookies
  await context.clearCookies();
  
  // Navigate to root first to ensure localStorage is available
  await page.goto('/');
  
  // Clear localStorage and sessionStorage
  await page.evaluate(() => {
    try {
      localStorage.clear();
      sessionStorage.clear();
    } catch (e) {
      // Ignore if storage is not available
      console.warn('Storage not available:', e);
    }
  });
}

/**
 * Logs in a user for testing.
 *
 * @param page - Playwright page object
 * @param email - User email (default: 'parent@example.com')
 * @param password - User password (default: 'password123')
 */
export async function login(
  page: Page,
  email: string = 'parent@example.com',
  password: string = 'password123'
) {
  await page.goto('/login');
  await page.getByLabel(/email/i).fill(email);
  await page.getByLabel(/password/i).fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  
  // Wait for navigation to dashboard
  await page.waitForURL('/dashboard', { timeout: 10000 });
}
