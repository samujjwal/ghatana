import { test, expect } from '@playwright/test';

/**
 * Plugins page E2E tests
 */
test.describe('Plugins', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/plugins');
  });

  test('should display the bundled plugin inventory shell', async ({ page }) => {
    await expect(page.getByTestId('plugins-page')).toBeVisible();
    await expect(page.locator('h1')).toContainText(/Plugins/i);
    await expect(page.getByTestId('plugins-header-detail')).toContainText(/bundled plugins/i);
  });

  test('should expose inventory stats and installed grid', async ({ page }) => {
    await expect(page.getByTestId('plugins-stats-grid')).toBeVisible();
    await expect(page.getByTestId('plugins-installed-grid')).toBeVisible();
  });

  test('should have search and filter controls for bundled plugins', async ({ page }) => {
    await page.getByTestId('plugins-search-input').fill('postgres');
    await expect(page.getByTestId('plugins-category-filter')).toBeVisible();
    await expect(page.getByTestId('plugins-status-filter')).toBeVisible();
  });

  test('should show the catalog boundary instead of a marketplace browser', async ({ page }) => {
    await page.getByTestId('plugins-tab-catalog').click();
    await expect(page.getByTestId('plugins-catalog-boundary')).toContainText(/marketplace browsing/i);
  });

  test('should show deployment guidance for bundled plugin changes', async ({ page }) => {
    await page.getByTestId('plugins-tab-delivery').click();
    await expect(page.getByTestId('plugins-delivery-guidance')).toContainText(/launcher build/i);
  });

  test('should open plugin details from the installed inventory when cards are present', async ({ page }) => {
    const firstDetailsLink = page.getByRole('link', { name: /details/i }).first();
    if (await firstDetailsLink.isVisible()) {
      await firstDetailsLink.click();
      await expect(page).toHaveURL(/\/plugins\//);
    }
  });
});
