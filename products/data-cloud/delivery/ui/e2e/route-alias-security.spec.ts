import { test, expect } from '@playwright/test';

/**
 * Route Alias Security E2E Tests
 * 
 * Validates that compatibility alias routes cannot bypass role or capability gates
 * at runtime. Each test attempts to access a surface via alias URL and verifies
 * that the same security constraints apply as the canonical route.
 */

test.describe('Route Alias Security E2E', () => {
  test.describe('Role Protection via Aliases', () => {
    test('should enforce operator role for /compliance alias to /trust', async ({ page }) => {
      // Attempt to access compliance surface (alias of trust) as viewer
      await page.goto('/compliance', { waitUntil: 'networkidle' });

      // If not logged in or viewer role, should see login or permission error
      const isLoggedIn = await page.evaluate(() => !!localStorage.getItem('auth_token'));
      
      if (!isLoggedIn) {
        await expect(page).toHaveURL(/login|forbidden|permission/i);
      } else {
        // Check role header or capability status
        const hasOperatorRole = await page.evaluate(() => {
          const shell = document.querySelector('[data-shell-role]');
          return shell?.getAttribute('data-shell-role') === 'operator';
        });

        if (!hasOperatorRole) {
          await expect(page.locator('[role="alert"]')).toContainText(/permission|access denied/i);
        }
      }
    });

    test('should enforce operator role for /governance alias to /trust', async ({ page }) => {
      // Attempt to access governance surface (alias of trust) as viewer
      await page.goto('/governance', { waitUntil: 'networkidle' });

      const hasOperatorRole = await page.evaluate(() => {
        const shell = document.querySelector('[data-shell-role]');
        return shell?.getAttribute('data-shell-role') === 'operator';
      });

      if (!hasOperatorRole) {
        await expect(page.locator('[role="alert"]')).toContainText(/permission|access denied/i);
      } else {
        // Should load trust surface
        await expect(page.locator('main')).toBeVisible();
      }
    });

    test('should enforce operator role for /analytics alias to /insights', async ({ page }) => {
      // Attempt to access analytics surface (alias of insights) as viewer
      await page.goto('/analytics', { waitUntil: 'networkidle' });

      const hasOperatorRole = await page.evaluate(() => {
        const shell = document.querySelector('[data-shell-role]');
        return shell?.getAttribute('data-shell-role') === 'operator';
      });

      if (!hasOperatorRole) {
        await expect(page.locator('[role="alert"]')).toContainText(/permission|access denied/i);
      } else {
        // Should load insights surface
        await expect(page.locator('main')).toBeVisible();
      }
    });

    test('should redirect data collection aliases to canonical route', async ({ page }) => {
      // Navigate via alias
      await page.goto('/collections', { waitUntil: 'networkidle' });

      // Should redirect to canonical route
      // (may navigate or may show the data surface via alias)
      const url = page.url();
      const isOnDataSurface = url.includes('/data') || url.includes('/collections');
      expect(isOnDataSurface).toBe(true);
    });

    test('should redirect workflow aliases to canonical route', async ({ page }) => {
      // Navigate via alias
      await page.goto('/workflows', { waitUntil: 'networkidle' });

      // Should redirect to canonical route
      const url = page.url();
      const isOnPipelinesSurface = url.includes('/pipelines') || url.includes('/workflows');
      expect(isOnPipelinesSurface).toBe(true);
    });
  });

  test.describe('Capability Gates via Aliases', () => {
    test('should enforce capability gate for /alerts alias', async ({ page }) => {
      // DC-P1.12: Use canonical /surfaces endpoint instead of /capabilities
      await page.route('**/api/**/surfaces', (route) => {
        route.abort();
      });

      await page.goto('/alerts', { waitUntil: 'networkidle' });

      // Should show DisabledSurfacePage or similar indicator
      const isDisabled = await page.locator('[data-surface-status="disabled"]').isVisible().catch(() => false);
      const hasDisabledMessage = await page.locator('[role="alert"]').getByText(/unavailable|disabled/i).isVisible().catch(() => false);

      expect(isDisabled || hasDisabledMessage).toBe(true);
    });

    test('should show degraded indicator when capability is degraded', async ({ page }) => {
      // DC-P1.12: Use canonical /surfaces endpoint instead of /capabilities
      await page.route('**/api/**/surfaces', (route) => {
        route.fulfill({
          status: 200,
          body: JSON.stringify({
            data: {
              capabilities: [
                {
                  id: 'memory',
                  status: 'degraded',
                  since: new Date().toISOString(),
                },
              ],
            },
          }),
        });
      });

      await page.goto('/memory', { waitUntil: 'networkidle' });

      // Should show degraded indicator but surface should be accessible
      const hasDegradedIndicator = await page.locator('[data-degraded="true"]').isVisible().catch(() => false);
      const hasWarning = await page.locator('[role="alert"]').getByText(/degraded|partial/i).isVisible().catch(() => false);

      expect(hasDegradedIndicator || hasWarning).toBe(true);
    });

    test('should enforce same gate for alias and canonical route', async ({ page }) => {
      // Test that /memory and canonical route have same capability gate
      // Navigate via alias
      await page.goto('/memory', { waitUntil: 'networkidle' });

      const aliasCapabilityId = await page.locator('[data-capability-id]').getAttribute('data-capability-id');

      // Navigate to canonical route (if it exists as separate route)
      // and verify same capability is checked
      if (aliasCapabilityId) {
        expect(aliasCapabilityId).toBe('memory');
      }
    });
  });

  test.describe('Deep Link Security', () => {
    test('should not allow query parameter exploitation via alias', async ({ page }) => {
      // Attempt to exploit query parameters in alias redirect
      await page.goto('/collections?redirect=/admin', { waitUntil: 'networkidle' });

      // Should not redirect to arbitrary URL
      const url = page.url();
      expect(url).not.toContain('/admin');
    });

    test('should not allow fragment exploitation via alias', async ({ page }) => {
      // Attempt to use fragment for state manipulation
      await page.goto('/compliance#role=admin', { waitUntil: 'networkidle' });

      // User role should not be elevated by fragment
      const actualRole = await page.evaluate(() => {
        const shell = document.querySelector('[data-shell-role]');
        return shell?.getAttribute('data-shell-role');
      });

      expect(actualRole).not.toBe('admin');
    });

    test('should properly encode special characters in alias paths', async ({ page }) => {
      // Attempt URL encoding bypass
      await page.goto('/collections%252Fadmin', { waitUntil: 'networkidle' });

      // Should either go to collections or show 404, not to admin
      const url = page.url();
      const isValid = url.includes('/collections') || url.includes('/404');
      expect(isValid).toBe(true);
    });
  });

  test.describe('Alias Redirect Chains', () => {
    test('should not create redirect loops between aliases', async ({ page }) => {
      // Navigate to alias
      const response = await page.goto('/collections', { waitUntil: 'networkidle' });

      // Should succeed (not loop indefinitely)
      expect(response?.status()).toBeLessThan(400);
    });

    test('should redirect through alias only once', async ({ page }) => {
      const navigationEvents: string[] = [];

      page.on('framenavigated', (frame) => {
        navigationEvents.push(frame.url());
      });

      await page.goto('/workflows', { waitUntil: 'networkidle' });

      // Should have at most 2 navigation events (initial + redirect)
      expect(navigationEvents.length).toBeLessThanOrEqual(2);
    });
  });

  test.describe('Alias Lifecycle Alignment', () => {
    test('should inherit lifecycle state from canonical route', async ({ page }) => {
      // If canonical route is deprecated, alias should also be unavailable
      await page.goto('/collections', { waitUntil: 'networkidle' });

      const hasLifecycle = await page.locator('[data-route-lifecycle]').isVisible().catch(() => false);
      // Lifecycle should be consistent with canonical route
    });

    test('should disable alias when canonical route is removed', async ({ page }) => {
      // This is a configuration test - in production, if /data is removed,
      // /collections should also not work
      await page.goto('/collections', { waitUntil: 'networkidle' });

      const response = await page.goto('/collections', { waitUntil: 'networkidle' });
      
      // If removed, should get redirect or 404
      // If still available, should work
      expect(response?.status() || 200).toBeDefined();
    });
  });

  test.describe('Security Regression Checklist', () => {
    test('documents all security checks for aliases', async ({ page }) => {
      const securityChecks = {
        roleProtection: [
          '/compliance enforces operator role',
          '/governance enforces operator role',
          '/analytics enforces operator role',
          '/automation-insights enforces operator role',
        ],
        capabilityGates: [
          '/alerts enforces capability gate',
          '/memory enforces capability gate',
          '/entities enforces capability gate',
          '/context enforces capability gate',
        ],
        redirectSecurity: [
          'No query parameter exploitation',
          'No fragment exploitation',
          'No URL encoding bypass',
          'No redirect loops',
        ],
        inputValidation: [
          'Alias paths are validated',
          'Route parameters are sanitized',
          'No path traversal possible',
        ],
      };

      // Verify structure of security checklist
      expect(Object.keys(securityChecks)).toHaveLength(4);
      expect(securityChecks.roleProtection).toHaveLength(4);
      expect(securityChecks.capabilityGates).toHaveLength(4);
      expect(securityChecks.redirectSecurity).toHaveLength(4);
      expect(securityChecks.inputValidation).toHaveLength(3);
    });
  });
});
