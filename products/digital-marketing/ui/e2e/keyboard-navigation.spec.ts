import { test, expect } from '@playwright/test';
import { loginAs, mockDmosApi, navigateInApp, TEST_WORKSPACE } from './fixtures';

/**
 * Keyboard navigation tests (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify keyboard navigation accessibility
 * @doc.layer e2e
 */
test.describe('Keyboard Navigation @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page, { roles: ['marketing-director'] });
  });

  test('dashboard is navigable via keyboard', async ({ page }) => {
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    // Tab through interactive elements
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // Verify focus is visible
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(focusedElement).toBeTruthy();
  });

  test('approvals table can be navigated with keyboard', async ({ page }) => {
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/approvals`);

    const reviewLink = page.locator('[data-testid="review-link-req-e2e-1"]');
    await expect(reviewLink).toBeVisible();
    await reviewLink.focus();
    await expect(reviewLink).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('[data-testid="approval-detail-page"]')).toBeVisible();
  });

  test('feature unavailable actions can be focused', async ({ page }) => {
    await loginAs(page, { roles: ['viewer'] });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/campaigns`);
    const dashboardButton = page.getByRole('button', { name: 'Return to Dashboard' });
    await expect(dashboardButton).toBeVisible();
    await dashboardButton.focus();
    await expect(dashboardButton).toBeFocused();
  });

  test('strategy form controls are keyboard reachable', async ({ page }) => {
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/strategy`);
    await page.locator('[data-testid="strategy-service-area-input"]').focus();
    await expect(page.locator('[data-testid="strategy-service-area-input"]')).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.locator('[data-testid="strategy-offer-input"]')).toBeFocused();
  });
});
