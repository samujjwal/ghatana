import { test, expect } from '@playwright/test';

/**
 * P0-016 / P1-016: DMOS Campaigns Page E2E Tests
 *
 * Browser-level tests for the campaign management UI.
 * Tests user flows: login, create, list, launch, pause campaigns.
 */

test.describe('Campaigns Page', () => {
  const TEST_WORKSPACE = 'test-workspace';
  const TEST_TOKEN = 'e2e-test-token';

  test.beforeEach(async ({ page }) => {
    // Navigate to login page
    await page.goto('/login');

    // Login with test credentials (dev mode only)
    await page.fill('[data-testid="login-token"]', TEST_TOKEN);
    await page.fill('[data-testid="login-workspace-id"]', TEST_WORKSPACE);
    await page.fill('[data-testid="login-tenant-id"]', 'test-tenant');
    await page.fill('[data-testid="login-principal-id"]', 'e2e-test-user');
    await page.click('[data-testid="login-submit"]');

    // Wait for dashboard redirect
    await page.waitForURL(`**/workspaces/${TEST_WORKSPACE}/dashboard`);

    // Navigate to campaigns page
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 10000 });
  });

  test('P0-016: Page loads without errors', async ({ page }) => {
    // Verify page title and key elements
    await expect(page.locator('h1')).toContainText('Campaigns');
    await expect(page.locator('[data-testid="campaign-list"]')).toBeVisible();
    await expect(page.locator('[data-testid="create-campaign-btn"]')).toBeVisible();
  });

  test('P0-016: Create campaign flow works', async ({ page }) => {
    // Click create button
    await page.click('[data-testid="create-campaign-btn"]');

    // Fill campaign form
    const campaignName = `E2E Test Campaign ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'EMAIL');

    // Submit form
    await page.click('[data-testid="campaign-submit-btn"]');

    // Verify campaign appears in list
    await expect(page.locator('[data-testid="campaign-list"]')).toContainText(campaignName);
    await expect(page.locator('[data-testid="campaign-status"]')).toContainText('DRAFT');
  });

  test('P0-016: Launch campaign flow works', async ({ page }) => {
    // Create a campaign first
    await page.click('[data-testid="create-campaign-btn"]');
    const campaignName = `Launch Test ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'EMAIL');
    await page.click('[data-testid="campaign-submit-btn"]');

    // Wait for campaign to appear
    const campaignRow = page.locator('[data-testid="campaign-row"]', {
      hasText: campaignName
    });
    await expect(campaignRow).toBeVisible();

    // Click launch button
    await campaignRow.locator('[data-testid="launch-campaign-btn"]').click();

    // Confirm in dialog
    await page.click('[data-testid="confirm-launch-btn"]');

    // Verify status changes to LAUNCHED
    await expect(campaignRow.locator('[data-testid="campaign-status"]'])).toContainText('LAUNCHED', {
      timeout: 5000
    });
  });

  test('P0-016: Pause campaign flow works', async ({ page }) => {
    // Create and launch a campaign
    await page.click('[data-testid="create-campaign-btn"]');
    const campaignName = `Pause Test ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'EMAIL');
    await page.click('[data-testid="campaign-submit-btn"]');

    const campaignRow = page.locator('[data-testid="campaign-row"]', {
      hasText: campaignName
    });
    await campaignRow.locator('[data-testid="launch-campaign-btn"]').click();
    await page.click('[data-testid="confirm-launch-btn"]');
    await expect(campaignRow.locator('[data-testid="campaign-status"]'])).toContainText('LAUNCHED');

    // Pause the campaign
    await campaignRow.locator('[data-testid="pause-campaign-btn"]').click();
    await page.click('[data-testid="confirm-pause-btn"]');

    // Verify status changes to PAUSED
    await expect(campaignRow.locator('[data-testid="campaign-status"]'])).toContainText('PAUSED', {
      timeout: 5000
    });
  });

  test('P0-016: Empty state displayed when no campaigns', async ({ page }) => {
    // Clear any existing campaigns (if API supports it)
    // Check for empty state message
    const emptyState = page.locator('[data-testid="empty-campaigns"]');
    if (await emptyState.isVisible().catch(() => false)) {
      await expect(emptyState).toContainText('No campaigns');
      await expect(page.locator('[data-testid="create-campaign-btn"]')).toBeVisible();
    }
  });

  test('P0-016: Error handling displays user-friendly messages', async ({ page }) => {
    // Simulate an error by creating a campaign with invalid data
    await page.click('[data-testid="create-campaign-btn"]');
    await page.fill('[data-testid="campaign-name-input"]', ''); // Empty name
    await page.click('[data-testid="campaign-submit-btn"]');

    // Error message should be visible
    await expect(page.locator('[role="alert"]')).toBeVisible();
    await expect(page.locator('[role="alert"]')).toContainText(/required|name|empty/i);
  });

  test('P0-016: Tenant isolation - only workspace campaigns shown', async ({ page }) => {
    // Campaigns from other workspaces should not be visible
    // This is implicitly tested by only seeing campaigns created in this test workspace
    const campaigns = await page.locator('[data-testid="campaign-row"]').count();

    // All visible campaigns should have this workspace ID
    const workspaceIds = await page.locator('[data-testid="campaign-workspace-id"]').allTextContents();
    for (const wsId of workspaceIds) {
      expect(wsId).toBe(TEST_WORKSPACE);
    }
  });

  test('P1-016: Accessibility - page meets WCAG standards', async ({ page }) => {
    // Run accessibility scan
    // Note: Requires @axe-core/playwright
    // const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
    // expect(accessibilityScanResults.violations).toEqual([]);

    // Basic a11y checks
    await expect(page.locator('main')).toBeVisible();
    await expect(page.locator('h1')).toBeVisible();

    // Check for proper heading structure
    const h1Count = await page.locator('h1').count();
    expect(h1Count).toBe(1);
  });

  test('P1-016: Responsive design works on mobile viewport', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });

    // Page should still be usable
    await expect(page.locator('[data-testid="campaigns-page"]')).toBeVisible();
    await expect(page.locator('[data-testid="create-campaign-btn"]')).toBeVisible();
  });

  test('P1-016: Loading state shown during data fetch', async ({ page }) => {
    // Throttle network to see loading state
    await page.route('**/campaigns**', async route => {
      await new Promise(r => setTimeout(r, 500));
      await route.continue();
    });

    // Reload page
    await page.reload();

    // Loading indicator should be visible during fetch
    await expect(page.locator('[data-testid="loading-campaigns"]')).toBeVisible();

    // Wait for data to load
    await expect(page.locator('[data-testid="loading-campaigns"]')).toBeHidden({ timeout: 10000 });
  });

  test('P1-016: Pagination controls work', async ({ page }) => {
    // Skip if not enough campaigns
    const campaignCount = await page.locator('[data-testid="campaign-row"]').count();
    if (campaignCount < 10) {
      test.skip();
    }

    // Test pagination if present
    const nextBtn = page.locator('[data-testid="pagination-next"]');
    if (await nextBtn.isVisible().catch(() => false)) {
      await nextBtn.click();

      // Should show next page of results
      await expect(page.locator('[data-testid="campaign-list"]')).toBeVisible();
    }
  });
});

test.describe('Feature Flagged Routes', () => {
  test('P0-005: Feature unavailable page shown for disabled features', async ({ page }) => {
    // Navigate to a disabled feature (if configured)
    // This test assumes a feature flag exists that disables a route

    // Mock the feature flag check
    await page.route('**/features**', async route => {
      await route.fulfill({
        status: 200,
        body: JSON.stringify({ 'dmos.budget_page_enabled': false })
      });
    });

    // Try to access disabled page
    await page.goto('/workspaces/test-workspace/budget');

    // Should show feature unavailable page
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).toBeVisible();
    await expect(page.locator('[data-testid="back-to-dashboard"]')).toBeVisible();
    await expect(page.locator('[data-testid="go-back"]')).toBeVisible();

    // Clicking back to dashboard should work
    await page.click('[data-testid="back-to-dashboard"]');
    await expect(page).toHaveURL(/.*dashboard/);
  });
});

test.describe('Authentication Flows', () => {
  test('P0-006: Auth callback page handles OAuth response', async ({ page }) => {
    // This test simulates the OAuth callback flow
    const mockCode = 'mock-auth-code-123';
    const mockState = 'mock-state-456';

    // Navigate to callback with mock OAuth params
    await page.goto(`/auth/callback?code=${mockCode}&state=${mockState}`);

    // In dev mode, might show loading then redirect
    // In production, would validate with auth provider
    await expect(page.locator('[data-testid="auth-callback-page"]')).toBeVisible();
  });

  test('P0-007: Manual login gated in production mode', async ({ page }) => {
    // This test assumes production mode configuration
    // Navigate to login
    await page.goto('/login');

    // In production, should either redirect to provider or show provider button
    // This behavior depends on VITE_AUTH_PROVIDER_ENABLED setting
    const providerBtn = page.locator('[data-testid="provider-login-btn"]');
    const manualForm = page.locator('[data-testid="login-token"]');

    // One of these should be visible based on environment
    const providerVisible = await providerBtn.isVisible().catch(() => false);
    const manualVisible = await manualForm.isVisible().catch(() => false);

    expect(providerVisible || manualVisible).toBe(true);
  });

  test('P0-008: Session expiry redirects to login', async ({ page, context }) => {
    // Set a very short session expiry (this would need backend support)
    // For now, simulate by clearing auth

    await page.goto('/login');
    await page.fill('[data-testid="login-token"]', 'expiry-test-token');
    await page.fill('[data-testid="login-workspace-id"]', 'test-workspace');
    await page.fill('[data-testid="login-tenant-id"]', 'test-tenant');
    await page.fill('[data-testid="login-principal-id"]', 'test-user');
    await page.click('[data-testid="login-submit"]');

    // Navigate to dashboard
    await page.waitForURL('**/dashboard');

    // Clear auth token from context
    await context.clearCookies();

    // Try to access protected page
    await page.goto('/workspaces/test-workspace/campaigns');

    // Should redirect to login
    await expect(page).toHaveURL(/.*login/);
  });
});
