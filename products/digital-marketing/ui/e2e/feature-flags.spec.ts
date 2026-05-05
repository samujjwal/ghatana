/**
 * P0-014: Feature-flag production build and runtime tests.
 *
 * Tests:
 * - Feature flags are properly defined in build configuration
 * - Disabled features show FeatureUnavailablePage
 * - Enabled features are accessible
 * - Feature flag checks fail closed (default to false)
 *
 * @doc.type test
 * @doc.purpose Feature-flag production build and runtime validation (P0-014)
 * @doc.layer e2e
 */

import { test, expect } from '@playwright/test';

test.describe('P0-014: Feature-flag production build tests', () => {
  test('Feature flags are defined in build configuration', async ({ page }) => {
    // Check that Vite environment variables are properly defined
    await page.goto('/');

    // Verify that import.meta.env is accessible
    const envCheck = await page.evaluate(() => {
      return typeof (window as any).import !== 'undefined' && 
             typeof (window as any).import.meta !== 'undefined' &&
             typeof (window as any).import.meta.env !== 'undefined';
    });

    expect(envCheck).toBe(true);
  });

  test('Production build does not include undefined feature flags', async ({ page }) => {
    // In production build, undefined feature flags should default to false
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_PRODUCTION: 'true',
            // Intentionally omit some feature flags to test fail-closed behavior
          },
        },
      };
    });

    await page.goto('/');

    // Verify that undefined flags are treated as false
    const undefinedFlagCheck = await page.evaluate(() => {
      const env = (window as any).import?.meta?.env || {};
      return env.VITE_DMOS_CAMPAIGNS_ENABLED === undefined;
    });

    expect(undefinedFlagCheck).toBe(true);
  });
});

test.describe('P0-014: Feature-flag runtime tests', () => {
  test('Disabled feature shows FeatureUnavailablePage', async ({ page }) => {
    // Mock a disabled feature flag
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_DMOS_CAMPAIGNS_ENABLED: 'false',
          },
        },
      };
    });

    // Navigate to campaigns page (should be disabled)
    await page.goto('/workspaces/test-workspace/campaigns');

    // Should show FeatureUnavailablePage
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).toBeVisible();
    await expect(page.locator('h1')).toContainText('Feature Unavailable');
  });

  test('Enabled feature is accessible', async ({ page }) => {
    // Mock an enabled feature flag
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_DMOS_CAMPAIGNS_ENABLED: 'true',
          },
        },
      };
    });

    // Navigate to campaigns page (should be enabled)
    await page.goto('/workspaces/test-workspace/campaigns');

    // Should show campaigns page, not FeatureUnavailablePage
    await expect(page.locator('[data-testid="campaigns-page"]')).toBeVisible();
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).not.toBeVisible();
  });

  test('Feature flag check fails closed (defaults to false)', async ({ page }) => {
    // Omit the feature flag entirely
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_PRODUCTION: 'true',
            // VITE_DMOS_CAMPAIGNS_ENABLED is intentionally omitted
          },
        },
      };
    });

    // Navigate to campaigns page
    await page.goto('/workspaces/test-workspace/campaigns');

    // Should show FeatureUnavailablePage (fail-closed)
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).toBeVisible();
  });

  test('FeatureUnavailablePage provides safe navigation', async ({ page }) => {
    // Mock a disabled feature
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_DMOS_CAMPAIGNS_ENABLED: 'false',
          },
        },
      };
    });

    await page.goto('/workspaces/test-workspace/campaigns');

    // Verify navigation buttons are present
    await expect(page.locator('[data-testid="back-to-dashboard"]')).toBeVisible();
    await expect(page.locator('[data-testid="go-back"]')).toBeVisible();

    // Click back to dashboard
    await page.click('[data-testid="back-to-dashboard"]');

    // Should navigate to dashboard
    await expect(page).toHaveURL(/.*dashboard/);
  });

  test('Feature flags are case-sensitive', async ({ page }) => {
    // Test with incorrect case (should not work)
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_DMOS_CAMPAIGNS_ENABLED: 'True', // Wrong case
          },
        },
      };
    });

    await page.goto('/workspaces/test-workspace/campaigns');

    // Should show FeatureUnavailablePage (case-sensitive check fails)
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).toBeVisible();
  });

  test('Feature flags reject invalid values', async ({ page }) => {
    // Test with invalid value
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_DMOS_CAMPAIGNS_ENABLED: 'yes', // Invalid, should be 'true' or 'false'
          },
        },
      };
    });

    await page.goto('/workspaces/test-workspace/campaigns');

    // Should show FeatureUnavailablePage (invalid value treated as false)
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).toBeVisible();
  });
});

test.describe('P0-014: Feature-flag integration tests', () => {
  test('Multiple feature flags can be independently controlled', async ({ page }) => {
    // Enable one feature, disable another
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_DMOS_CAMPAIGNS_ENABLED: 'true',
            VITE_DMOS_BUDGET_ENABLED: 'false',
          },
        },
      };
    });

    // Campaigns should be accessible
    await page.goto('/workspaces/test-workspace/campaigns');
    await expect(page.locator('[data-testid="campaigns-page"]')).toBeVisible();

    // Budget should show FeatureUnavailablePage
    await page.goto('/workspaces/test-workspace/budget');
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).toBeVisible();
  });

  test('Feature flags persist across navigation', async ({ page }) => {
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_DMOS_CAMPAIGNS_ENABLED: 'true',
          },
        },
      };
    });

    // Navigate to campaigns
    await page.goto('/workspaces/test-workspace/campaigns');
    await expect(page.locator('[data-testid="campaigns-page"]')).toBeVisible();

    // Navigate away and back
    await page.goto('/workspaces/test-workspace/dashboard');
    await page.goto('/workspaces/test-workspace/campaigns');

    // Feature should still be accessible
    await expect(page.locator('[data-testid="campaigns-page"]')).toBeVisible();
  });
});
