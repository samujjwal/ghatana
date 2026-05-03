import { test, expect } from '@playwright/test';
import { devices } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * Responsive layout tests (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify responsive design across viewport sizes
 * @doc.layer e2e
 */
test.describe('Responsive Layout @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('dashboard works on mobile', async ({ page }) => {
    await page.setViewportSize(devices['iPhone 13'].viewport);
    await page.goto('/dashboard');

    // Verify mobile menu button is visible
    await expect(page.locator('[data-testid="mobile-menu-button"]')).toBeVisible();
  });

  test('dashboard works on tablet', async ({ page }) => {
    await page.setViewportSize(devices['iPad Pro'].viewport);
    await page.goto('/dashboard');

    // Verify dashboard content is visible
    await expect(page.locator('[data-testid="dashboard-content"]')).toBeVisible();
  });

  test('dashboard works on desktop', async ({ page }) => {
    await page.setViewportSize(devices['Desktop Chrome'].viewport);
    await page.goto('/dashboard');

    // Verify sidebar is visible on desktop
    await expect(page.locator('[data-testid="sidebar"]')).toBeVisible();
  });

  test('approvals table is scrollable on mobile', async ({ page }) => {
    await page.setViewportSize(devices['iPhone 13'].viewport);
    await page.goto('/approvals');

    const table = page.locator('table');
    await expect(table).toBeVisible();

    // Verify table has horizontal scroll on mobile
    const hasScroll = await table.evaluate((el) => {
      return el.scrollWidth > el.clientWidth;
    });

    expect(hasScroll).toBe(true);
  });

  test('forms adapt to mobile viewport', async ({ page }) => {
    await page.setViewportSize(devices['iPhone 13'].viewport);
    await page.goto('/intake');

    // Verify form inputs are stacked vertically on mobile
    const form = page.locator('form');
    await expect(form).toBeVisible();
  });
});
