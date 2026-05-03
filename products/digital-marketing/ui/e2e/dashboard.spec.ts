/**
 * E2E: Dashboard page user journeys.
 */
import { test, expect } from '@playwright/test';
import { loginAs, mockDmosApi, TEST_WORKSPACE } from './fixtures';

test.describe('dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page);
    await page.waitForURL(new RegExp(`/workspaces/${TEST_WORKSPACE}/dashboard`));
  });

  test('renders the dashboard page', async ({ page }) => {
    await expect(page.locator('[data-testid="dashboard-page"]')).toBeVisible();
  });

  test('shows the approval widget with pending count', async ({ page }) => {
    await expect(page.locator('[data-testid="approval-widget"]')).toBeVisible();
  });

  test('shows the workflow status widget', async ({ page }) => {
    await expect(page.locator('[data-testid="workflow-status-widget"]')).toBeVisible();
  });

  test('shows the risk compliance widget', async ({ page }) => {
    await expect(page.locator('[data-testid="risk-compliance-widget"]')).toBeVisible();
  });

  test('navigates to approval queue on approval widget click', async ({ page }) => {
    await page.locator('[data-testid="approval-widget"]').getByRole('link').first().click();
    await expect(page).toHaveURL(new RegExp(`/workspaces/${TEST_WORKSPACE}/approvals`));
  });
});
