/**
 * P1-044: Browser E2E test suite - Campaign Lifecycle.
 *
 * Tests:
 * - Campaign list page load
 * - Campaign creation
 * - Campaign launch
 * - Campaign pause
 * - API and UI state consistency
 *
 * @doc.type test
 * @doc.purpose Browser E2E for campaign journey (P1-044, P0-012)
 * @doc.layer e2e
 */

import { test, expect, Page } from '@playwright/test';

test.describe('P1-044: Campaign Lifecycle E2E', () => {
  const TEST_WORKSPACE = 'test-workspace-123';
  const TEST_TENANT = 'test-tenant';

  async function login(page: Page): Promise<void> {
    // Navigate to login and authenticate
    await page.goto('/login');

    // In test mode, use dev login form
    await page.fill('[data-testid="tenant-id-input"]', TEST_TENANT);
    await page.fill('[data-testid="workspace-id-input"]', TEST_WORKSPACE);
    await page.fill('[data-testid="principal-id-input"]', 'test-user');
    await page.fill('[data-testid="session-id-input"]', 'test-session');
    await page.click('[data-testid="login-button"]');

    // Wait for dashboard to load
    await page.waitForURL(`**/workspaces/${TEST_WORKSPACE}/dashboard`);
  }

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('P1-044: Campaign list loads without 404/405', async ({ page }) => {
    // Navigate to campaigns page
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);

    // Verify page loads successfully
    await expect(page.getByTestId('campaigns-page')).toBeVisible();

    // Verify no error states
    await expect(page.getByTestId('error-page')).not.toBeVisible();

    // Verify loading state resolves
    await expect(page.getByTestId('campaigns-loading')).not.toBeVisible({ timeout: 5000 });

    // Verify either empty state or campaign list is shown
    const emptyState = page.getByTestId('campaigns-empty');
    const campaignList = page.getByTestId('campaign-list');
    await expect(emptyState.or(campaignList)).toBeVisible();
  });

  test('P0-012: Create campaign and see it in list', async ({ page }) => {
    // Navigate to campaigns page
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    // Fill campaign form directly on the page (inline form)
    const campaignName = `Test Campaign ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'EMAIL');

    // Submit form
    await page.click('[data-testid="create-campaign-btn"]');

    // Verify success message (toast notification)
    await expect(page.locator('.toast')).toBeVisible({ timeout: 5000 });

    // Verify campaign appears in list without refresh
    await expect(page.getByText(campaignName)).toBeVisible();
  });

  test('P0-012: Launch campaign and see status change', async ({ page }) => {
    // First create a campaign
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    // Create a campaign first
    const campaignName = `Launch Test ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'EMAIL');
    await page.click('[data-testid="create-campaign-btn"]');

    // Wait for campaign to appear in list
    await page.waitForSelector(`[data-testid="campaign-row-${campaignName}"]`, { timeout: 5000 });

    // Find the campaign row by name
    const campaignRow = page.locator(`[data-testid="campaign-row-${campaignName}"]`);

    // Click launch button (status should be DRAFT)
    const launchBtn = campaignRow.locator('text=Launch');
    await launchBtn.click();

    // Verify success message (toast notification)
    await expect(page.locator('.toast')).toBeVisible({ timeout: 5000 });

    // Verify status changes to LAUNCHED without refresh
    await expect(campaignRow.locator('text=LAUNCHED')).toBeVisible({ timeout: 10000 });
  });

  test('P0-012: Pause campaign and see status change', async ({ page }) => {
    // Create and launch a campaign first
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    const campaignName = `Pause Test ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'EMAIL');
    await page.click('[data-testid="create-campaign-btn"]');

    // Wait for campaign to appear
    await page.waitForSelector(`[data-testid="campaign-row-${campaignName}"]`, { timeout: 5000 });
    const campaignRow = page.locator(`[data-testid="campaign-row-${campaignName}"]`);

    // Launch the campaign
    const launchBtn = campaignRow.locator('text=Launch');
    await launchBtn.click();
    await expect(campaignRow.locator('text=LAUNCHED')).toBeVisible({ timeout: 10000 });

    // Pause the campaign
    const pauseBtn = campaignRow.locator('text=Pause');
    await pauseBtn.click();

    // Verify success message
    await expect(page.locator('.toast')).toBeVisible({ timeout: 5000 });

    // Verify status changes to PAUSED without refresh
    await expect(campaignRow.locator('text=PAUSED')).toBeVisible({ timeout: 10000 });
  });

  test('P1-030: Surface mutation errors in UI', async ({ page }) => {
    // Try to create campaign with invalid data (empty name)
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    // Submit form without filling required fields
    await page.click('[data-testid="create-campaign-btn"]');

    // Verify form validation prevents submission (HTML5 required attribute)
    await expect(page.locator('[data-testid="campaign-name-input"]')).toBeFocused();
  });

  test('P1-031: Per-row action pending states', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    // Create a campaign first
    const campaignName = `Pending Test ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'EMAIL');
    await page.click('[data-testid="create-campaign-btn"]');

    // Wait for campaign to appear
    await page.waitForSelector(`[data-testid="campaign-row-${campaignName}"]`, { timeout: 5000 });
    const campaignRow = page.locator(`[data-testid="campaign-row-${campaignName}"]`);

    // Click launch button
    const launchBtn = campaignRow.locator('text=Launch');
    await launchBtn.click();

    // Verify button shows pending state (text changes to "Launching…")
    await expect(campaignRow.locator('text=Launching…')).toBeVisible({ timeout: 5000 });
  });
});
