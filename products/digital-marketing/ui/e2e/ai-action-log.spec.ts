/**
 * E2E: AI Action Log page user journeys.
 */
import { test, expect } from '@playwright/test';
import { loginAs, mockDmosApi, navigateInApp, TEST_WORKSPACE, AI_ACTION } from './fixtures';

test.describe('AI action log', () => {
  test.beforeEach(async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page);
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/ai-actions`);
    await expect(page.locator('[data-testid="ai-action-log-page"]')).toBeVisible();
  });

  test('renders the AI action log page', async ({ page }) => {
    await expect(page.locator('[data-testid="ai-action-log-page"]')).toBeVisible();
  });

  test('shows AI action entry from mock data', async ({ page }) => {
    await expect(
      page.locator(`[data-testid="ai-action-log-item-${AI_ACTION.actionId}"]`),
    ).toBeVisible();
  });

  test('shows empty state when no actions', async ({ page }) => {
    await page.route(
      `**/v1/workspaces/${TEST_WORKSPACE}/ai-actions`,
      (route) => route.fulfill({ json: { items: [] } }),
    );
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/ai-actions`);
    await expect(page.locator('[data-testid="ai-action-log-page-empty"]')).toBeVisible();
  });

  test('shows error state when API fails @a11y', async ({ page }) => {
    await page.route(
      `**/v1/workspaces/${TEST_WORKSPACE}/ai-actions`,
      (route) => route.fulfill({ status: 500, body: 'Server Error' }),
    );
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/ai-actions`);
    await expect(page.locator('[data-testid="ai-action-log-page-error"]')).toBeVisible();
  });
});
