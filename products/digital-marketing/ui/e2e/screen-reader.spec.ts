import { test, expect } from '@playwright/test';
import { loginAs, mockDmosApi, navigateInApp, TEST_WORKSPACE } from './fixtures';

/**
 * Screen reader label checks (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify screen reader accessibility with proper labels
 * @doc.layer e2e
 */
test.describe('Screen Reader Labels @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page, { roles: ['marketing-director'] });
  });

  test('dashboard has proper ARIA labels', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    // Check for landmark regions
    const main = page.locator('main');
    await expect(main).toBeVisible();

    const nav = page.locator('nav');
    await expect(nav).toBeVisible();
  });

  test('form inputs have associated labels', async ({ page }) => {
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/strategy`);

    // Check that inputs have labels
    const serviceAreaInput = page.locator('[data-testid="strategy-service-area-input"]');
    await expect(serviceAreaInput).toBeVisible();

    await expect(page.getByLabel('Service Area')).toBeVisible();
  });

  test('buttons have accessible names', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/approvals`);

    const reviewLink = page.locator('[data-testid="review-link-req-e2e-1"]');
    await expect(reviewLink).toBeVisible();

    const hasAccessibleName = await reviewLink.evaluate((el) => {
      return el.textContent?.trim().length > 0 || el.getAttribute('aria-label') !== null || el.getAttribute('aria-labelledby') !== null;
    });

    expect(hasAccessibleName).toBe(true);
  });

  test('tables have proper headers', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/approvals`);

    const table = page.locator('table');
    await expect(table).toBeVisible();

    const hasHeaders = await table.evaluate((el) => {
      const headers = el.querySelectorAll('th');
      return headers.length > 0;
    });

    expect(hasHeaders).toBe(true);
  });

  test('images have alt text', async ({ page }) => {
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    const images = page.locator('img');
    const count = await images.count();

    for (let i = 0; i < count; i++) {
      const hasAlt = await images.nth(i).evaluate((el) => {
        return el.getAttribute('alt') !== null || el.getAttribute('role') === 'presentation';
      });
      expect(hasAlt).toBe(true);
    }
  });
});
