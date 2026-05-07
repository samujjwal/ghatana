import { test, expect } from '@playwright/test';

/**
 * Settings boundary page E2E tests
 */
test.describe('Settings', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/settings');
  });

  test('should display the settings boundary page', async ({ page }) => {
    await expect(page.getByTestId('settings-page')).toBeVisible();
    await expect(page.getByTestId('settings-boundary-note')).toContainText(/boundary surface/i);
  });

  test('should show all settings boundary sections', async ({ page }) => {
    await expect(page.getByTestId('settings-tab-profile')).toBeVisible();
    await expect(page.getByTestId('settings-tab-preferences')).toBeVisible();
    await expect(page.getByTestId('settings-tab-notifications')).toBeVisible();
    await expect(page.getByTestId('settings-tab-api')).toBeVisible();
  });

  test('should navigate between settings boundary sections', async ({ page }) => {
    await page.getByTestId('settings-tab-preferences').click();
    await expect(page.getByTestId('settings-section-preferences')).toBeVisible();
  });

  test('should show the API keys boundary section', async ({ page }) => {
    await page.getByTestId('settings-tab-api').click();
    await expect(page.getByTestId('settings-section-api-keys')).toBeVisible();
    await expect(page.locator('body')).toContainText(/API Documentation/i);
  });

  test('should navigate to settings from the admin sidebar', async ({ page }) => {
    await page.goto('/');
    const settingsLink = page.getByRole('link', { name: /settings/i }).first();
    if (await settingsLink.isVisible()) {
      await settingsLink.click();
      await expect(page).toHaveURL(/\/settings/);
    }
  });
});
