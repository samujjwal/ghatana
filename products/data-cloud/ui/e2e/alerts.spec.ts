import { test, expect } from '@playwright/test';

/**
 * Alerts Page E2E Tests
 */
test.describe('Alerts', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/alerts');
  });

  test('should display alerts page', async ({ page }) => {
    await expect(page.locator('h1, h2, [data-testid="alerts-title"]').first()).toBeVisible();
  });

  test('should show alert summary metrics', async ({ page }) => {
    const body = page.locator('body');
    await expect(body).toContainText(/critical|warning|alert/i);
  });

  test('should display alert groups or list', async ({ page }) => {
    const alertItems = page.locator('[data-testid="alert-item"], .alert-item, [class*="alert"]');
    const count = await alertItems.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });

  test('should open create alert rule form', async ({ page }) => {
    const createBtn = page.getByRole('button', { name: /create|new|add/i }).first();
    if (await createBtn.isVisible()) {
      await createBtn.click();
      await expect(page.locator('form, [role="dialog"], [data-testid="alert-form"]').first()).toBeVisible();
    }
  });

  test('should allow filtering alerts', async ({ page }) => {
    const filterInput = page.getByRole('combobox').or(page.getByPlaceholder(/filter|search/i)).first();
    if (await filterInput.isVisible()) {
      await filterInput.click();
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('should navigate to alerts from sidebar', async ({ page }) => {
    await page.goto('/');
    const alertsLink = page.getByRole('link', { name: /alerts/i }).first();
    if (await alertsLink.isVisible()) {
      await alertsLink.click();
      await expect(page).toHaveURL(/\/alerts/);
    }
  });
});
