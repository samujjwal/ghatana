import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * Screen reader label checks (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify screen reader accessibility with proper labels
 * @doc.layer e2e
 */
test.describe('Screen Reader Labels @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('dashboard has proper ARIA labels', async ({ page }) => {
    await page.goto('/dashboard');

    // Check for landmark regions
    const main = page.locator('main');
    await expect(main).toBeVisible();

    const nav = page.locator('nav');
    await expect(nav).toBeVisible();
  });

  test('form inputs have associated labels', async ({ page }) => {
    await page.goto('/intake');

    // Check that inputs have labels
    const serviceAreaInput = page.locator('input[name="serviceArea"]');
    await expect(serviceAreaInput).toBeVisible();

    const hasLabel = await serviceAreaInput.evaluate((el) => {
      const id = el.getAttribute('id');
      if (id) {
        const label = document.querySelector(`label[for="${id}"]`);
        return label !== null;
      }
      return el.getAttribute('aria-label') !== null || el.getAttribute('aria-labelledby') !== null;
    });

    expect(hasLabel).toBe(true);
  });

  test('buttons have accessible names', async ({ page }) => {
    await page.goto('/approvals');

    const approveButton = page.locator('[data-testid="approve-button"]');
    await expect(approveButton).toBeVisible();

    const hasAccessibleName = await approveButton.evaluate((el) => {
      return el.textContent?.trim().length > 0 || el.getAttribute('aria-label') !== null || el.getAttribute('aria-labelledby') !== null;
    });

    expect(hasAccessibleName).toBe(true);
  });

  test('tables have proper headers', async ({ page }) => {
    await page.goto('/approvals');

    const table = page.locator('table');
    await expect(table).toBeVisible();

    const hasHeaders = await table.evaluate((el) => {
      const headers = el.querySelectorAll('th');
      return headers.length > 0;
    });

    expect(hasHeaders).toBe(true);
  });

  test('images have alt text', async ({ page }) => {
    await page.goto('/dashboard');

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
