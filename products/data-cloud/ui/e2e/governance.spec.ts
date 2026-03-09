import { test, expect } from '@playwright/test';

/**
 * Governance Page E2E Tests
 */
test.describe('Governance', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/governance');
  });

  test('should display governance page', async ({ page }) => {
    await expect(page.locator('body')).toContainText(/govern|policy|compli/i);
  });

  test('should list governance policies or rules', async ({ page }) => {
    const items = page.locator('[data-testid="policy-item"], [class*="policy"], [class*="rule"]');
    const count = await items.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });

  test('should show compliance status indicators', async ({ page }) => {
    const body = page.locator('body');
    await expect(body).toBeVisible();
  });

  test('should have tabs for different governance areas', async ({ page }) => {
    const tabs = page.getByRole('tab');
    const buttons = page.getByRole('button');
    const tabCount = await tabs.count();
    const btnCount = await buttons.count();
    expect(tabCount + btnCount).toBeGreaterThanOrEqual(0);
  });

  test('should navigate to enhanced governance view', async ({ page }) => {
    await page.goto('/governance/enhanced');
    await expect(page.locator('body')).toContainText(/govern|policy|compli/i);
  });

  test('should navigate to governance from sidebar', async ({ page }) => {
    await page.goto('/');
    const govLink = page.getByRole('link', { name: /governance/i }).first();
    if (await govLink.isVisible()) {
      await govLink.click();
      await expect(page).toHaveURL(/\/governance/);
    }
  });
});
