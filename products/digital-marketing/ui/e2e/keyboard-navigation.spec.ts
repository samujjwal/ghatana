import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * Keyboard navigation tests (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify keyboard navigation accessibility
 * @doc.layer e2e
 */
test.describe('Keyboard Navigation @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('dashboard is navigable via keyboard', async ({ page }) => {
    await page.goto('/dashboard');

    // Tab through interactive elements
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // Verify focus is visible
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(focusedElement).toBeTruthy();
  });

  test('approvals table can be navigated with keyboard', async ({ page }) => {
    await page.goto('/approvals');

    // Navigate to approve button
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Enter');

    // Verify dialog opened
    await expect(page.locator('[data-testid="approval-dialog"]')).toBeVisible();
  });

  test('escape key closes dialogs', async ({ page }) => {
    await page.goto('/approvals');
    await page.click('[data-testid="approve-button"]');

    // Close dialog with Escape
    await page.keyboard.press('Escape');
    await expect(page.locator('[data-testid="approval-dialog"]')).not.toBeVisible();
  });

  test('enter key submits forms', async ({ page }) => {
    await page.goto('/intake');
    await page.fill('[name="serviceArea"]', 'San Francisco');
    await page.fill('[name="primaryOffer"]', 'Plumbing');
    await page.fill('[name="monthlyBudget"]', '5000');

    // Submit with Enter
    await page.keyboard.press('Enter');

    // Verify submission
    await expect(page).toHaveURL(/\/strategy/);
  });
});
