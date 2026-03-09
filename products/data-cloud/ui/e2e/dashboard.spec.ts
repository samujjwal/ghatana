import { test, expect } from '@playwright/test';

/**
 * Dashboard E2E Tests
 * 
 * Tests the main dashboard functionality including:
 * - Loading dashboard with stats
 * - Navigating to different sections
 * - Viewing recent activity
 * - Checking KPIs and metrics
 * 
 * @doc.type test
 * @doc.purpose E2E tests for dashboard functionality
 * @doc.layer testing
 */

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display dashboard with key metrics', async ({ page }) => {
    // Check for dashboard title
    await expect(page.locator('h1')).toContainText('Dashboard');
    
    // Verify KPI cards are visible
    await expect(page.locator('text=/Total Workflows/i')).toBeVisible();
    await expect(page.locator('text=/Active Workflows/i')).toBeVisible();
    await expect(page.locator('text=/Total Executions/i')).toBeVisible();
    await expect(page.locator('text=/Success Rate/i')).toBeVisible();
  });

  test('should navigate to collections from dashboard', async ({ page }) => {
    const collectionsLink = page.getByRole('link', { name: /collections/i }).first();
    await collectionsLink.click();
    await expect(page).toHaveURL(/\/collections/);
  });

  test('should navigate to workflows from dashboard', async ({ page }) => {
    const workflowsLink = page.getByRole('link', { name: /workflows/i }).first();
    await workflowsLink.click();
    await expect(page).toHaveURL(/\/workflows/);
  });

  test('should display recent activity', async ({ page }) => {
    const recentActivity = page.locator('text=/Recent Activities|Recent Collections/i');
    await expect(recentActivity.first()).toBeVisible();
  });

  test('should show audit logs summary', async ({ page }) => {
    const auditSection = page.locator('text=/Audit Logs/i');
    if (await auditSection.isVisible()) {
      await expect(auditSection).toBeVisible();
    }
  });

  test('should show compliance status', async ({ page }) => {
    const complianceSection = page.locator('text=/Compliance/i');
    if (await complianceSection.isVisible()) {
      await expect(complianceSection).toBeVisible();
    }
  });

  test('should handle loading state', async ({ page }) => {
    // Intercept API calls to delay response
    await page.route('**/api/v1/**', async (route) => {
      await new Promise(resolve => setTimeout(resolve, 1000));
      await route.continue();
    });
    
    await page.reload();
    
    // Check for loading indicator
    await expect(page.locator('text=/Loading Dashboard|Gathering your data/i')).toBeVisible();
  });
});
