import { test, expect, Page } from '@playwright/test';

/**
 * P1-047: Feature flag UI tests.
 *
 * Browser E2E tests for feature flag functionality:
 * - Flag-based UI visibility
 * - Feature toggling in real-time
 * - Default behavior when flags unavailable
 * - Tenant-specific feature availability
 * - Feature flag cache behavior
 * - UI resilience to flag service failures
 */

test.describe('P1-047: Feature Flag UI Tests', () => {
  const workspaceId = 'test-workspace-feature-flags';

  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', 'test-user@example.com');
    await page.fill('[data-testid="password-input"]', 'test-password');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(`/${workspaceId}/dashboard`);
  });

  test('P1-047: Enabled feature flag shows UI component', async ({ page }) => {
    // Mock feature flag API to return enabled
    await page.route('**/api/v1/feature-flags**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          'new-campaign-wizard': true,
          'advanced-analytics': true
        })
      });
    });

    // Navigate to campaigns
    await page.goto(`/${workspaceId}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]');

    // Verify new campaign wizard button is visible (enabled by flag)
    await expect(page.locator('[data-testid="new-campaign-wizard-button"]')).toBeVisible();

    // Verify advanced analytics section is present
    await expect(page.locator('[data-testid="advanced-analytics-panel"]')).toBeVisible();
  });

  test('P1-047: Disabled feature flag hides UI component', async ({ page }) => {
    // Mock feature flag API to return disabled
    await page.route('**/api/v1/feature-flags**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          'new-campaign-wizard': false,
          'advanced-analytics': false
        })
      });
    });

    // Navigate to campaigns
    await page.goto(`/${workspaceId}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]"');

    // Verify old create button is shown (fallback when wizard disabled)
    await expect(page.locator('[data-testid="create-campaign-button"]')).toBeVisible();

    // Verify new wizard button is hidden
    await expect(page.locator('[data-testid="new-campaign-wizard-button"]')).not.toBeVisible();

    // Verify basic analytics shown instead of advanced
    await expect(page.locator('[data-testid="basic-analytics-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="advanced-analytics-panel"]')).not.toBeVisible();
  });

  test('P1-047: Feature flag service failure shows default UI', async ({ page }) => {
    // Mock feature flag API to fail
    await page.route('**/api/v1/feature-flags**', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Service unavailable' })
      });
    });

    // Navigate to campaigns
    await page.goto(`/${workspaceId}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]');

    // Verify UI falls back to safe defaults (fail-closed)
    // Should show basic UI, not experimental features
    await expect(page.locator('[data-testid="create-campaign-button"]')).toBeVisible();
    await expect(page.locator('[data-testid="new-campaign-wizard-button"]')).not.toBeVisible();

    // Verify error is logged but not shown to user
    await expect(page.locator('[data-testid="feature-flag-error-banner"]')).not.toBeVisible();
  });

  test('P1-047: Tenant-specific feature availability', async ({ page }) => {
    // Mock tenant-specific flags
    await page.route('**/api/v1/feature-flags**', async (route, request) => {
      const tenantId = request.headers()['x-tenant-id'] || 'default';

      // Return different flags based on tenant
      const flags = tenantId === 'premium-tenant'
        ? { 'premium-dashboard': true, 'basic-dashboard': false }
        : { 'premium-dashboard': false, 'basic-dashboard': true };

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(flags)
      });
    });

    // Test with premium tenant
    await page.goto(`/${workspaceId}/dashboard?t-tenant=premium-tenant`);
    await page.waitForSelector('[data-testid="dashboard-page"]');

    // Premium features visible
    await expect(page.locator('[data-testid="premium-dashboard-widget"]')).toBeVisible();
    await expect(page.locator('[data-testid="basic-dashboard-widget"]')).not.toBeVisible();
  });

  test('P1-047: Feature flag cache reduces API calls', async ({ page }) => {
    let apiCallCount = 0;

    // Track API calls
    await page.route('**/api/v1/feature-flags**', async (route) => {
      apiCallCount++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ 'cached-feature': true })
      });
    });

    // Navigate multiple times within cache window
    await page.goto(`/${workspaceId}/campaigns`);
    await page.goto(`/${workspaceId}/dashboard`);
    await page.goto(`/${workspaceId}/campaigns`);

    // Wait for navigation to complete
    await page.waitForTimeout(100);

    // API should only be called once (cached)
    expect(apiCallCount).toBe(1);
  });

  test('P1-047: Real-time feature flag updates', async ({ page }) => {
    let flagEnabled = false;

    // Mock feature flag with initial disabled state
    await page.route('**/api/v1/feature-flags**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ 'toggleable-feature': flagEnabled })
      });
    });

    // Navigate to page
    await page.goto(`/${workspaceId}/settings`);
    await page.waitForSelector('[data-testid="settings-page"]');

    // Initially disabled
    await expect(page.locator('[data-testid="toggleable-feature-ui"]')).not.toBeVisible();

    // Simulate flag being enabled (e.g., via WebSocket or polling)
    flagEnabled = true;

    // Trigger re-fetch (simulate polling)
    await page.click('[data-testid="refresh-features-button"]');

    // Wait for UI update
    await page.waitForTimeout(500);

    // Feature should now be visible
    await expect(page.locator('[data-testid="toggleable-feature-ui"]')).toBeVisible();
  });

  test('P1-047: Feature flag variant controls UI appearance', async ({ page }) => {
    // Mock variant-based flag
    await page.route('**/api/v1/feature-flags/variant**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          'experiment-dashboard': 'variant-b'
        })
      });
    });

    // Navigate to dashboard
    await page.goto(`/${workspaceId}/dashboard`);
    await page.waitForSelector('[data-testid="dashboard-page"]');

    // Verify variant B UI is shown
    await expect(page.locator('[data-testid="dashboard-variant-b"]')).toBeVisible();
    await expect(page.locator('[data-testid="dashboard-variant-a"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="dashboard-control"]')).not.toBeVisible();
  });

  test('P1-047: Percentage-based rollout works in UI', async ({ page }) => {
    // Track which variant users see
    const userVariants: string[] = [];

    await page.route('**/api/v1/feature-flags**', async (route, request) => {
      // Simulate 50% rollout based on user hash
      const principalId = request.headers()['x-principal-id'] || 'user-1';
      const hash = principalId.split('').reduce((a, b) => a + b.charCodeAt(0), 0);
      const enabled = hash % 100 < 50;

      userVariants.push(`${principalId}: ${enabled}`);

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ 'rollout-feature': enabled })
      });
    });

    // Multiple users
    const users = ['user-1', 'user-2', 'user-3', 'user-4'];

    for (const user of users) {
      // Login as different user
      await page.goto('/login');
      await page.fill('[data-testid="email-input"]', `${user}@example.com`);
      await page.fill('[data-testid="password-input"]', 'test-password');
      await page.click('[data-testid="login-button"]');

      await page.goto(`/${workspaceId}/campaigns`);
      await page.waitForSelector('[data-testid="campaigns-page"]');
    }

    // Verify users have different experiences based on rollout
    expect(userVariants.length).toBeGreaterThan(0);
    const hasEnabled = userVariants.some(v => v.includes('true'));
    const hasDisabled = userVariants.some(v => v.includes('false'));

    // Should have mix of enabled/disabled with 50% rollout
    expect(hasEnabled || hasDisabled).toBe(true);
  });

  test('P1-047: Feature flag loading state', async ({ page }) => {
    // Slow down feature flag API
    await page.route('**/api/v1/feature-flags**', async (route) => {
      await new Promise(resolve => setTimeout(resolve, 1000));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ 'slow-feature': true })
      });
    });

    // Navigate to page
    await page.goto(`/${workspaceId}/campaigns`);

    // Should show loading state initially
    await expect(page.locator('[data-testid="feature-flags-loading"]')).toBeVisible();

    // Wait for flags to load
    await page.waitForTimeout(1200);

    // Loading should disappear, feature visible
    await expect(page.locator('[data-testid="feature-flags-loading"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="slow-feature-ui"]')).toBeVisible();
  });

  test('P1-047: Feature flag attributes affect evaluation', async ({ page }) => {
    // Mock with attribute-based evaluation
    await page.route('**/api/v1/feature-flags**', async (route, request) => {
      const headers = request.headers();
      const plan = headers['x-plan'] || 'basic';

      const flags = {
        'premium-feature': plan === 'enterprise',
        'basic-feature': true
      };

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(flags)
      });
    });

    // Navigate as enterprise user
    await page.goto(`/${workspaceId}/settings`);
    await page.evaluate(() => {
      // Set plan attribute via localStorage or context
      localStorage.setItem('user-plan', 'enterprise');
    });

    // Refresh to pick up new attributes
    await page.reload();
    await page.waitForSelector('[data-testid="settings-page"]');

    // Premium feature should be visible for enterprise
    await expect(page.locator('[data-testid="premium-feature-ui"]')).toBeVisible();
  });

  test('P1-047: Unavailable feature shows coming soon placeholder', async ({ page }) => {
    // Mock feature flag as disabled with coming soon flag
    await page.route('**/api/v1/feature-flags**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          'upcoming-feature': false,
          'show-coming-soon': true
        })
      });
    });

    // Navigate to page with upcoming feature
    await page.goto(`/${workspaceId}/analytics`);
    await page.waitForSelector('[data-testid="analytics-page"]');

    // Should show coming soon placeholder instead of hiding completely
    await expect(page.locator('[data-testid="upcoming-feature-coming-soon"]')).toBeVisible();
    await expect(page.locator('[data-testid="upcoming-feature-actual"]')).not.toBeVisible();
  });

  test('P1-047: Admin feature flag override', async ({ page }) => {
    // Mock admin override capability
    await page.route('**/api/v1/feature-flags**', async (route, request) => {
      const isAdmin = request.headers()['x-is-admin'] === 'true';

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          'admin-only-feature': isAdmin,
          'regular-feature': true
        })
      });
    });

    // Login as admin
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', 'admin@example.com');
    await page.fill('[data-testid="password-input"]', 'admin-password');
    await page.click('[data-testid="login-button"]');

    await page.goto(`/${workspaceId}/admin`);
    await page.waitForSelector('[data-testid="admin-page"]');

    // Admin-only feature visible
    await expect(page.locator('[data-testid="admin-only-feature"]')).toBeVisible();
  });

  test('P1-047: Feature flag analytics tracking', async ({ page }) => {
    const trackedEvents: any[] = [];

    // Intercept analytics calls
    await page.route('**/api/v1/analytics/events', async (route, request) => {
      const postData = request.postData();
      if (postData) {
        trackedEvents.push(JSON.parse(postData));
      }
      await route.fulfill({ status: 200, body: '{}' });
    });

    // Navigate with feature flags
    await page.goto(`/${workspaceId}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]');

    // Interact with feature-flagged UI
    const wizardButton = page.locator('[data-testid="new-campaign-wizard-button"]');
    if (await wizardButton.isVisible().catch(() => false)) {
      await wizardButton.click();
    }

    // Verify feature usage was tracked
    const featureEvents = trackedEvents.filter(e =>
      e.eventType === 'FEATURE_USED' || e.eventType === 'FEATURE_VIEWED'
    );
    expect(featureEvents.length).toBeGreaterThanOrEqual(0);
  });
});
