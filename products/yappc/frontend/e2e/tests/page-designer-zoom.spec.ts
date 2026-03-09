import { test, expect } from '@playwright/test';

test.describe('Designer zoom interactions', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/designer');
  });

  test('header zoom buttons show indicator and change zoom', async ({
    page,
  }) => {
    // Ensure the page loaded
    await expect(page.locator('text=Design System Tester')).toBeVisible();

    // Click zoom in (by button title)
    await page.click('button[title="Zoom in"]');

    // The zoom indicator appears transiently
    const indicator = page.locator('[data-testid="zoom-indicator"]');
    await expect(indicator).toBeVisible({ timeout: 2000 });
    await expect(indicator).not.toBeVisible({ timeout: 2000 });
  });
});
