/**
 * E2E: Login page user journeys.
 *
 * P0-013: Production-mode auth E2E test
 */
import { test, expect } from '@playwright/test';
import { loginAs, mockDmosApi, TEST_WORKSPACE } from './fixtures';

test.describe('login', () => {
  test('renders the login form', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();
    await expect(page.locator('[data-testid="login-token"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-workspace-id"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-tenant-id"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-principal-id"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-submit"]')).toBeVisible();
  });

  test('shows validation error when fields are empty', async ({ page }) => {
    await page.goto('/login');
    await page.click('[data-testid="login-submit"]');
    await expect(page.getByRole('alert')).toContainText(/required/i);
  });

  test('redirects to dashboard after successful login', async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page);
    await expect(page).toHaveURL(
      new RegExp(`/workspaces/${TEST_WORKSPACE}/dashboard`),
    );
    await expect(page.locator('[data-testid="dashboard-page"]')).toBeVisible();
  });

  test('redirects unauthenticated access to /login', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/dashboard`);
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('P0-013: Production-mode auth E2E', () => {
  test('OAuth2 provider login flow works in production mode', async ({ page }) => {
    // Mock production environment variable
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_AUTH_PROVIDER_ENABLED: 'true',
            VITE_PRODUCTION: 'true',
          },
        },
      };
    });

    await page.goto('/login');

    // In production mode with auth provider, should show provider login button
    // and hide manual login form
    await expect(page.locator('[data-testid="provider-login-btn"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-token"]')).not.toBeVisible();
  });

  test('Manual login is blocked in production mode', async ({ page }) => {
    // Mock production environment variable
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_AUTH_PROVIDER_ENABLED: 'true',
            VITE_PRODUCTION: 'true',
          },
        },
      };
    });

    await page.goto('/login');

    // Try to manually submit login form (should be hidden)
    const manualForm = page.locator('[data-testid="login-token"]');
    if (await manualForm.isVisible().catch(() => false)) {
      await page.fill('[data-testid="login-token"]', 'test-token');
      await page.fill('[data-testid="login-workspace-id"]', TEST_WORKSPACE);
      await page.fill('[data-testid="login-tenant-id"]', 'test-tenant');
      await page.fill('[data-testid="login-principal-id"]', 'test-user');
      await page.click('[data-testid="login-submit"]');

      // Should fail with production error
      await expect(page.getByRole('alert')).toContainText(/not allowed|production/i);
    }
  });

  test('OAuth2 callback handles token exchange', async ({ page }) => {
    // Mock the OAuth callback endpoint
    await page.route('**/auth/callback', async route => {
      await route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token: 'mock-jwt-token',
          expiresAt: Date.now() + 3600000,
          workspaceId: TEST_WORKSPACE,
          tenantId: 'test-tenant',
          principalId: 'test-user',
          sessionId: 'test-session',
          roles: ['USER'],
        }),
      });
    });

    // Navigate to callback with mock OAuth params
    await page.goto('/auth/callback?code=mock-code&state=mock-state');

    // Should redirect to dashboard after successful token exchange
    await page.waitForURL(`**/workspaces/${TEST_WORKSPACE}/dashboard`);
    await expect(page.locator('[data-testid="dashboard-page"]')).toBeVisible();
  });

  test('Session refresh works in production mode', async ({ page }) => {
    // Mock production environment and auth refresh endpoint
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_AUTH_PROVIDER_ENABLED: 'true',
            VITE_PRODUCTION: 'true',
          },
        },
      };
    });

    await page.route('**/auth/refresh', async route => {
      await route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token: 'refreshed-jwt-token',
          expiresAt: Date.now() + 3600000,
          roles: ['USER'],
        }),
      });
    });

    // Login first
    await page.goto('/login');
    await page.click('[data-testid="provider-login-btn"]');

    // Mock the callback to complete login
    await page.waitForURL('**/auth/callback*');
    await page.route('**/auth/callback', async route => {
      await route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token: 'initial-jwt-token',
          expiresAt: Date.now() + 3600000,
          workspaceId: TEST_WORKSPACE,
          tenantId: 'test-tenant',
          principalId: 'test-user',
          sessionId: 'test-session',
          roles: ['USER'],
        }),
      });
    });

    // Navigate to dashboard
    await page.waitForURL(`**/workspaces/${TEST_WORKSPACE}/dashboard`);
    await expect(page.locator('[data-testid="dashboard-page"]')).toBeVisible();

    // Wait for session refresh to trigger (set to run periodically)
    // Verify no logout occurs (refresh succeeded)
    await page.waitForTimeout(5000);
    await expect(page).toHaveURL(/.*dashboard/);
  });

  test('Failed session refresh logs user out (fail closed)', async ({ page }) => {
    // Mock production environment
    await page.addInitScript(() => {
      (window as any).import = {
        meta: {
          env: {
            VITE_AUTH_PROVIDER_ENABLED: 'true',
            VITE_PRODUCTION: 'true',
          },
        },
      };
    });

    // Mock failed refresh endpoint
    await page.route('**/auth/refresh', async route => {
      await route.fulfill({
        status: 401,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ error: 'Invalid token' }),
      });
    });

    // Login first
    await page.goto('/login');
    await page.route('**/auth/callback', async route => {
      await route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token: 'initial-jwt-token',
          expiresAt: Date.now() + 1000, // Short expiry to trigger refresh
          workspaceId: TEST_WORKSPACE,
          tenantId: 'test-tenant',
          principalId: 'test-user',
          sessionId: 'test-session',
          roles: ['USER'],
        }),
      });
    });
    await page.click('[data-testid="provider-login-btn"]');

    // Navigate to dashboard
    await page.waitForURL(`**/workspaces/${TEST_WORKSPACE}/dashboard`);

    // Wait for session expiry and refresh attempt
    // Should redirect to login on refresh failure
    await page.waitForTimeout(6000);
    await expect(page).toHaveURL(/.*login/);
  });
});
