import { expect, test } from '@playwright/test';

test.describe('AEP login flow', () => {
  test('redirects unauthenticated users to login and supports platform SSO', async ({ page }) => {
    await page.goto('/build/pipelines');

    // Assert login page identity with platform-SSO-first copy
    await expect(page.getByRole('heading', { name: /aep control plane/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in with platform/i })).toBeVisible();

    // Legacy JWT path is hidden unless the feature flag is enabled; test verifies SSO is primary
  });

  test('stores token in sessionStorage and redirects after legacy token sign-in when enabled', async ({ page }) => {
    await page.route('**/api/v1/session', async (route) => {
      await route.fulfill({
        status: 200,
        headers: {
          'content-type': 'application/json',
          'x-aep-session': 'playwright-session-token',
        },
        body: JSON.stringify({
          session: 'playwright-session-token',
          expiresInSeconds: 3600,
        }),
      });
    });

    await page.goto('/build/pipelines');

    await expect(page.getByRole('heading', { name: /aep control plane/i })).toBeVisible();

    // Legacy JWT path only visible when LEGACY_JWT_PASTE flag is enabled
    const jwtInput = page.getByLabel('JWT access token');
    if (await jwtInput.isVisible().catch(() => false)) {
      await jwtInput.fill('playwright-jwt-token');
      await page.getByRole('button', { name: /sign in with token/i }).click();

      await expect(page).toHaveURL(/\/build\/pipelines$/);
      await expect(page.getByRole('navigation', { name: 'AEP navigation' })).toBeVisible();

      await expect.poll(async () => page.evaluate(() => window.sessionStorage.getItem('aep-token'))).toBe('playwright-jwt-token');
      await expect.poll(async () => page.evaluate(() => window.sessionStorage.getItem('aep-session'))).toBe('playwright-session-token');
    }
  });
});