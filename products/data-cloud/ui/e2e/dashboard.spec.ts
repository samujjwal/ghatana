import { test, expect } from '@playwright/test';

/**
 * Intelligent Hub E2E Tests
 * 
 * Tests the main home-surface functionality including:
 * - Loading the Intelligent Hub
 * - Navigating to outcome-first actions
 * - Viewing recent activity
 * - Checking lightweight summaries and role-aware disclosure
 * 
 * @doc.type test
 * @doc.purpose E2E tests for Intelligent Hub functionality
 * @doc.layer testing
 */

test.describe('Intelligent Hub', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display the outcome-first home surface', async ({ page }) => {
    await expect(page.getByTestId('intelligent-hub-page')).toBeVisible();
    await expect(page.getByTestId('intelligent-hub-outcome-section')).toBeVisible();
    await expect(page.locator('text=/Start with an outcome/i')).toBeVisible();
    await expect(page.locator('text=/Launch the next action without choosing the architecture first/i')).toBeVisible();
    await expect(page.getByRole('button', { name: /explore data/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /build pipeline/i })).toBeVisible();
  });

  test('should navigate to data explorer from the home surface', async ({ page }) => {
    await page.getByRole('button', { name: /explore data/i }).click();
    await expect(page).toHaveURL(/\/data/);
  });

  test('should navigate to pipeline creation from the home surface', async ({ page }) => {
    await page.getByRole('button', { name: /build pipeline/i }).click();
    await expect(page).toHaveURL(/\/pipelines\/new/);
  });

  test('should display recent activity', async ({ page }) => {
    await expect(page.getByTestId('intelligent-hub-recent-activity')).toBeVisible();
    await expect(page.locator('text=/Recent Activity/i')).toBeVisible();
  });

  test('should show quick actions and recommendations instead of legacy dashboard panels', async ({ page }) => {
    await expect(page.getByTestId('intelligent-hub-quick-actions')).toBeVisible();
    await expect(page.getByTestId('intelligent-hub-recommendations')).toBeVisible();
    await expect(page.locator('text=/Recommended Next Steps/i')).toBeVisible();
  });

  test('should show the lightweight insights summary cards', async ({ page }) => {
    await expect(page.getByTestId('intelligent-hub-insights')).toBeVisible();
    await expect(page.locator('text=/Active Pipelines|Total Collections/i')).toBeVisible();
  });

  test('should handle loading state', async ({ page }) => {
    await page.route('**/api/v1/**', async (route) => {
      await new Promise(resolve => setTimeout(resolve, 1000));
      await route.continue();
    });

    await page.reload();

    await expect(page.locator('text=/Loading|Gathering your data/i')).toBeVisible();
  });
});
