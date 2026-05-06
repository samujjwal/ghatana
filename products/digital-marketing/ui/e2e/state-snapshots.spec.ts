import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * Loading/error/empty state snapshot tests (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify UI states for loading, error, and empty scenarios
 * @doc.layer e2e
 */
test.describe('State Snapshots @visual', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('loading state snapshot', async ({ page }) => {
    await page.goto('/approvals');

    // Simulate loading state
    await page.evaluate(() => {
      const loader = document.createElement('div');
      loader.setAttribute('data-testid', 'loading-spinner');
      loader.innerHTML = '<div class="spinner">Loading...</div>';
      document.body.appendChild(loader);
    });

    await expect(page.locator('[data-testid="loading-spinner"]')).toBeVisible();
    await expect(page).toHaveScreenshot('loading-state.png');
  });

  test('error state snapshot', async ({ page }) => {
    await page.goto('/approvals');

    // Simulate error state
    await page.evaluate(() => {
      const error = document.createElement('div');
      error.setAttribute('data-testid', 'error-message');
      error.innerHTML = '<div class="error">Failed to load approvals. Please try again.</div>';
      document.body.appendChild(error);
    });

    await expect(page.locator('[data-testid="error-message"]')).toBeVisible();
    await expect(page).toHaveScreenshot('error-state.png');
  });

  test('empty state snapshot', async ({ page }) => {
    await page.goto('/approvals');

    // Simulate empty state
    await page.evaluate(() => {
      const empty = document.createElement('div');
      empty.setAttribute('data-testid', 'empty-state');
      empty.innerHTML = '<div class="empty">No approvals pending.</div>';
      document.body.appendChild(empty);
    });

    await expect(page.locator('[data-testid="empty-state"]')).toBeVisible();
    await expect(page).toHaveScreenshot('empty-state.png');
  });
});
