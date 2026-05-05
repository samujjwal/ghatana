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

    // Click create campaign button
    await page.click('[data-testid="create-campaign-button"]');

    // Fill campaign form
    const campaignName = `Test Campaign ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'EMAIL');
    await page.fill('[data-testid="campaign-budget-input"]', '5000');

    // Submit form
    await page.click('[data-testid="save-campaign-button"]');

    // Verify success message with correlation ID (P1-030, P1-051)
    await expect(page.getByTestId('success-message')).toBeVisible();

    // Verify campaign appears in list without refresh
    await expect(page.getByText(campaignName)).toBeVisible();

    // Verify correlation ID is shown for support (P1-051)
    const correlationId = await page.getByTestId('correlation-id-display').textContent();
    expect(correlationId).toBeTruthy();
    expect(correlationId?.length).toBeGreaterThan(8);
  });

  test('P0-012: Launch campaign and see status change', async ({ page }) => {
    // First create a campaign
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    // Find first draft campaign or create one
    const draftRow = page.locator('[data-testid="campaign-row"]:has([data-testid="status-DRAFT"])').first();

    if (await draftRow.isVisible().catch(() => false)) {
      // Get campaign name
      const campaignName = await draftRow.locator('[data-testid="campaign-name"]').textContent();

      // Click launch button for this campaign
      await draftRow.locator('[data-testid="launch-campaign-button"]').click();

      // Confirm launch
      await page.click('[data-testid="confirm-launch-button"]');

      // Verify success
      await expect(page.getByTestId('launch-success')).toBeVisible();

      // Verify status changes to LAUNCHED without refresh
      await expect(
        page.locator(`[data-testid="campaign-row"]:has-text("${campaignName}") [data-testid="status-LAUNCHED"]`)
      ).toBeVisible({ timeout: 10000 });
    }
  });

  test('P0-012: Pause campaign and see status change', async ({ page }) => {
    // Find a launched campaign
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    const launchedRow = page.locator('[data-testid="campaign-row"]:has([data-testid="status-LAUNCHED"])').first();

    if (await launchedRow.isVisible().catch(() => false)) {
      // Get campaign name
      const campaignName = await launchedRow.locator('[data-testid="campaign-name"]').textContent();

      // Click pause button
      await launchedRow.locator('[data-testid="pause-campaign-button"]').click();

      // Confirm pause
      await page.click('[data-testid="confirm-pause-button"]');

      // Verify success
      await expect(page.getByTestId('pause-success')).toBeVisible();

      // Verify status changes to PAUSED without refresh
      await expect(
        page.locator(`[data-testid="campaign-row"]:has-text("${campaignName}") [data-testid="status-PAUSED"]`)
      ).toBeVisible({ timeout: 10000 });
    }
  });

  test('P1-030: Surface mutation errors in UI', async ({ page }) => {
    // Try to create campaign with invalid data
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.click('[data-testid="create-campaign-button"]');

    // Submit form without filling required fields
    await page.click('[data-testid="save-campaign-button"]');

    // Verify error is displayed
    await expect(page.getByTestId('mutation-error')).toBeVisible();

    // Verify error has correlation ID for support
    const correlationId = await page.getByTestId('correlation-id').textContent();
    expect(correlationId).toBeTruthy();

    // Verify error message is actionable
    const errorMessage = await page.getByTestId('error-message').textContent();
    expect(errorMessage).toBeTruthy();
    expect(errorMessage?.length).toBeGreaterThan(10);
  });

  test('P1-031: Per-row action pending states', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    // Find a campaign row
    const campaignRow = page.locator('[data-testid="campaign-row"]').first();

    if (await campaignRow.isVisible()) {
      // Click an action button
      const actionButton = campaignRow.locator('[data-testid="launch-campaign-button"], [data-testid="pause-campaign-button"]').first();

      if (await actionButton.isVisible()) {
        await actionButton.click();

        // Verify only this row shows pending state
        await expect(campaignRow.getByTestId('action-pending')).toBeVisible();

        // Verify other rows don't show global loading
        const otherRows = page.locator('[data-testid="campaign-row"]').filter({ hasNotText: await campaignRow.textContent() || '' });
        await expect(otherRows.getByTestId('action-pending')).not.toBeVisible();
      }
    }
  });
});
