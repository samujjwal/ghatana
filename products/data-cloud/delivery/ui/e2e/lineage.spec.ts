import { test, expect } from '@playwright/test';

/**
 * Lineage preview E2E tests
 */
test.describe('Lineage Preview', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/lineage');
  });

  test('should redirect the legacy lineage alias to the canonical data explorer preview', async ({ page }) => {
    await expect(page).toHaveURL(/\/data\?view=lineage/);
    await expect(page.getByTestId('data-explorer-page')).toBeVisible();
  });

  test('should keep lineage as a data-explorer preview mode', async ({ page }) => {
    await expect(page.getByTestId('data-explorer-view-toggle')).toBeVisible();
    await expect(page.getByTestId('collection-view-lineage')).toBeVisible();
  });

  test('should let users switch into lineage mode from the canonical explorer', async ({ page }) => {
    await page.goto('/data');
    await page.getByTestId('collection-view-lineage').click();
    await expect(page).toHaveURL(/\/data\?view=lineage/);
  });

  test('should expose collection search while previewing lineage from the explorer shell', async ({ page }) => {
    await page.goto('/data?view=lineage');
    await page.getByTestId('collection-search-input').fill('orders');
    await expect(page.getByTestId('data-explorer-page')).toBeVisible();
  });
});
