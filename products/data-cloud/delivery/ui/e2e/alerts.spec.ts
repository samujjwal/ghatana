import { test, expect } from '@playwright/test';

/**
 * Alerts page E2E tests
 */
test.describe('Alerts', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/alerts');
  });

  test('should display the live alerts triage shell', async ({ page }) => {
    await expect(page.getByTestId('alerts-page')).toBeVisible();
    await expect(page.locator('h1')).toContainText(/Alerts/i);
  });

  test('should show the alerts truth panel', async ({ page }) => {
    await expect(page.getByTestId('alerts-truth-panel')).toBeVisible();
    await expect(page.locator('body')).toContainText(/Route Coverage|Stream Health|Grouped Coverage|Suggestion Coverage/i);
  });

  test('should keep rule management collapsed until requested', async ({ page }) => {
    await expect(page.getByRole('button', { name: /create alert rule/i })).toHaveCount(0);
    await page.getByTestId('alert-rule-management-toggle').click();
    await expect(page.getByTestId('alert-rule-management-panel')).toContainText(/Create Alert Rule/i);
  });

  test('should expose grouped triage and list-view controls', async ({ page }) => {
    await expect(page.locator('body')).toContainText(/AI Grouped|List View/i);
  });

  test('should render alert list cards when incidents are present', async ({ page }) => {
    const alertItems = page.getByTestId('alert-item');
    if (await alertItems.count()) {
      await expect(alertItems.first()).toBeVisible();
    }
  });
});
