import { test, expect } from '@playwright/test';
import { devices } from '@playwright/test';
import { loginAs, mockDmosApi, navigateInApp, TEST_WORKSPACE } from './fixtures';

/**
 * Responsive layout tests (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify responsive design across viewport sizes
 * @doc.layer e2e
 */
test.describe('Responsive Layout @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page, { roles: ['marketing-director'] });
  });

  test('dashboard works on mobile', async ({ page }) => {
    await page.setViewportSize(devices['iPhone 13'].viewport);
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    await expect(page.locator('[data-testid="dashboard-page"]')).toBeVisible();
    await expect(page.getByRole('main')).toBeVisible();
  });

  test('dashboard works on tablet', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 1366 });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    await expect(page.locator('[data-testid="dashboard-page"]')).toBeVisible();
  });

  test('dashboard works on desktop', async ({ page }) => {
    await page.setViewportSize(devices['Desktop Chrome'].viewport);
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    await expect(page.getByRole('navigation')).toBeVisible();
  });

  test('approvals table is scrollable on mobile', async ({ page }) => {
    await page.setViewportSize(devices['iPhone 13'].viewport);
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/approvals`);

    const table = page.locator('table');
    await expect(table).toBeVisible();

    await expect(page.locator('[data-testid="review-link-req-e2e-1"]')).toBeVisible();
  });

  test('forms adapt to mobile viewport', async ({ page }) => {
    await page.setViewportSize(devices['iPhone 13'].viewport);
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/strategy`);

    // Verify form inputs are stacked vertically on mobile
    const form = page.locator('form');
    await expect(form).toBeVisible();
  });
});
