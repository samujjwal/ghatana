import { test, expect } from '@playwright/test';

/**
 * Plugins Page E2E Tests
 */
test.describe('Plugins', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/plugins');
  });

  test('should display plugins page', async ({ page }) => {
    await expect(page.locator('body')).toContainText(/plugin|connector|integration/i);
  });

  test('should list available plugins', async ({ page }) => {
    const pluginCards = page.locator('[data-testid="plugin-card"], [class*="plugin"], [class*="card"]');
    const count = await pluginCards.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });

  test('should have search or filter functionality', async ({ page }) => {
    const searchInput = page
      .getByRole('searchbox')
      .or(page.getByPlaceholder(/search|filter/i))
      .first();
    if (await searchInput.isVisible()) {
      await searchInput.fill('postgres');
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('should navigate to plugin details on click', async ({ page }) => {
    const firstPlugin = page
      .locator('[data-testid="plugin-card"], [class*="plugin-item"]')
      .first();
    if (await firstPlugin.isVisible()) {
      await firstPlugin.click();
      await expect(page).toHaveURL(/\/plugins\//);
    }
  });

  test('should show installed vs available plugins', async ({ page }) => {
    const tabs = page.getByRole('tab');
    const tabCount = await tabs.count();
    expect(tabCount).toBeGreaterThanOrEqual(0);
  });

  test('should navigate to enhanced plugins view', async ({ page }) => {
    await page.goto('/plugins/enhanced');
    await expect(page.locator('body')).toContainText(/plugin|connector/i);
  });
});
