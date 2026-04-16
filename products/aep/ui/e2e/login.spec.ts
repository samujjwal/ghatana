import { expect, test } from '@playwright/test';

test.describe('AEP login flow', () => {
  test('stores token and redirects to the requested page after sign in', async ({ page }) => {
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

    await expect(page.getByRole('heading', { name: /enter the aep control plane/i })).toBeVisible();
    await page.getByLabel('JWT access token').fill('playwright-jwt-token');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/build\/pipelines$/);
    await expect(page.getByRole('navigation', { name: 'AEP navigation' })).toBeVisible();

    await expect.poll(async () => page.evaluate(() => window.localStorage.getItem('aep-token'))).toBe('playwright-jwt-token');
    await expect.poll(async () => page.evaluate(() => window.localStorage.getItem('aep-session'))).toBe('playwright-session-token');
  });
});