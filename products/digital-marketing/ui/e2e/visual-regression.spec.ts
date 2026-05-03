import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * Visual regression tests (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify visual consistency with screenshots
 * @doc.layer e2e
 */
test.describe('Visual Regression @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('dashboard visual snapshot', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveScreenshot('dashboard.png');
  });

  test('approvals page visual snapshot', async ({ page }) => {
    await page.goto('/approvals');
    await expect(page).toHaveScreenshot('approvals.png');
  });

  test('strategy page visual snapshot', async ({ page }) => {
    await page.goto('/strategy');
    await expect(page).toHaveScreenshot('strategy.png');
  });

  test('content page visual snapshot', async ({ page }) => {
    await page.goto('/content');
    await expect(page).toHaveScreenshot('content.png');
  });

  test('campaigns page visual snapshot', async ({ page }) => {
    await page.goto('/campaigns');
    await expect(page).toHaveScreenshot('campaigns.png');
  });

  test('leads page visual snapshot', async ({ page }) => {
    await page.goto('/leads');
    await expect(page).toHaveScreenshot('leads.png');
  });

  test('analytics page visual snapshot', async ({ page }) => {
    await page.goto('/analytics');
    await expect(page).toHaveScreenshot('analytics.png');
  });

  test('connectors page visual snapshot', async ({ page }) => {
    await page.goto('/connectors');
    await expect(page).toHaveScreenshot('connectors.png');
  });

  test('AI recommendations page visual snapshot', async ({ page }) => {
    await page.goto('/ai-recommendations');
    await expect(page).toHaveScreenshot('ai-recommendations.png');
  });
});
