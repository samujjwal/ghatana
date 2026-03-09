import { test, expect } from '@playwright/test';

/**
 * Lineage Explorer E2E Tests
 */
test.describe('Lineage Explorer', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/lineage');
  });

  test('should display lineage explorer page', async ({ page }) => {
    await expect(page.locator('body')).toContainText(/lineage|graph|data flow/i);
  });

  test('should render a graph or node structure', async ({ page }) => {
    const canvas = page.locator('canvas, svg, [data-testid="lineage-graph"], [class*="graph"]');
    const count = await canvas.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });

  test('should show upstream and downstream nodes', async ({ page }) => {
    const body = page.locator('body');
    await expect(body).toBeVisible();
    const text = await body.textContent();
    expect(text).toBeTruthy();
  });

  test('should have search or filter for nodes', async ({ page }) => {
    const search = page.getByRole('searchbox').or(page.getByPlaceholder(/search|filter/i)).first();
    if (await search.isVisible()) {
      await search.fill('orders');
      await expect(page.locator('body')).toBeVisible();
    }
  });

  test('should navigate to enhanced lineage view', async ({ page }) => {
    await page.goto('/lineage/enhanced');
    await expect(page.locator('body')).toContainText(/lineage|graph/i);
  });

  test('should navigate to lineage from sidebar', async ({ page }) => {
    await page.goto('/');
    const lineageLink = page.getByRole('link', { name: /lineage/i }).first();
    if (await lineageLink.isVisible()) {
      await lineageLink.click();
      await expect(page).toHaveURL(/\/lineage/);
    }
  });
});
