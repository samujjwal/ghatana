import { test, expect } from '@playwright/test';

/**
 * Settings Page E2E Tests
 */
test.describe('Settings', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/settings');
  });

  test('should display settings page', async ({ page }) => {
    await expect(page.locator('body')).toContainText(/settings|profile|preference/i);
  });

  test('should show all settings sections', async ({ page }) => {
    await expect(page.locator('body')).toContainText('Profile');
    await expect(page.locator('body')).toContainText('Preferences');
    await expect(page.locator('body')).toContainText('Notifications');
    await expect(page.locator('body')).toContainText('API Keys');
  });

  test('should navigate between settings sections', async ({ page }) => {
    const prefsBtn = page.getByText('Preferences').first();
    if (await prefsBtn.isVisible()) {
      await prefsBtn.click();
      await expect(page.locator('body')).toContainText(/preference/i);
    }
  });

  test('should show API keys section', async ({ page }) => {
    const apiBtn = page.getByText('API Keys').first();
    if (await apiBtn.isVisible()) {
      await apiBtn.click();
      await expect(page.locator('body')).toContainText(/api|key/i);
    }
  });

  test('should navigate to settings from sidebar', async ({ page }) => {
    await page.goto('/');
    const settingsLink = page.getByRole('link', { name: /settings/i }).first();
    if (await settingsLink.isVisible()) {
      await settingsLink.click();
      await expect(page).toHaveURL(/\/settings/);
    }
  });
});
