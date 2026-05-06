import { test, expect } from '@playwright/test';
import {
  APPROVAL_APPROVED,
  APPROVAL_PENDING,
  AI_ACTION,
  TEST_TENANT,
  TEST_WORKSPACE,
  loginAs,
  mockDmosApi,
} from './fixtures';

test.describe('DMOS visual regression @visual', () => {
  test('dashboard shell matches baseline', async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page, { roles: ['marketing-director'] });
    await page.goto(`/workspaces/${TEST_WORKSPACE}/dashboard`);
    await expect(page.locator('[data-testid="dashboard-page"]')).toBeVisible();
    await expect(page).toHaveScreenshot('dmos-dashboard-shell.png');
  });

  test('permission denied shell matches baseline', async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page, { roles: [] });
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).toBeVisible();
    await expect(page).toHaveScreenshot('dmos-permission-denied-shell.png');
  });

  test('loading state matches baseline', async ({ page }) => {
    let releaseResponse: (() => void) | null = null;
    const responseBarrier = new Promise<void>((resolve) => {
      releaseResponse = resolve;
    });

    await page.route(`**/v1/workspaces/${TEST_WORKSPACE}/approvals/pending/*`, async (route) => {
      await responseBarrier;
      await route.fulfill({ json: { items: [APPROVAL_PENDING, APPROVAL_APPROVED] } });
    });

    await loginAs(page, { roles: ['marketing-director'] });
    await page.goto(`/workspaces/${TEST_WORKSPACE}/approvals`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-testid="approval-queue-loading"]')).toBeVisible();
    await expect(page).toHaveScreenshot('dmos-approval-loading.png');

    releaseResponse?.();
  });

  test('error state matches baseline', async ({ page }) => {
    await page.route(`**/v1/workspaces/${TEST_WORKSPACE}/approvals/pending/*`, async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Approval queue unavailable' }),
      });
    });

    await loginAs(page, { roles: ['marketing-director'] });
    await page.goto(`/workspaces/${TEST_WORKSPACE}/approvals`);
    await expect(page.locator('[data-testid="approval-queue-error"]')).toBeVisible();
    await expect(page).toHaveScreenshot('dmos-approval-error.png');
  });

  test('empty state matches baseline', async ({ page }) => {
    await page.route(`**/v1/workspaces/${TEST_WORKSPACE}/ai-actions`, async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      });
    });
    await page.route(`**/v1/workspaces/${TEST_WORKSPACE}/approvals/pending/${TEST_TENANT}`, async (route) => {
      await route.fulfill({ json: { items: [APPROVAL_PENDING] } });
    });
    await page.route(`**/v1/workspaces/${TEST_WORKSPACE}/approvals/${APPROVAL_PENDING.requestId}`, async (route) => {
      await route.fulfill({ json: APPROVAL_PENDING });
    });
    await page.route(
      `**/v1/workspaces/${TEST_WORKSPACE}/approvals/${APPROVAL_PENDING.requestId}/snapshot`,
      async (route) => {
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            requestId: APPROVAL_PENDING.requestId,
            targetType: APPROVAL_PENDING.targetType,
            targetId: APPROVAL_PENDING.targetId,
            targetWorkspaceId: TEST_WORKSPACE,
            snapshotSummary: APPROVAL_PENDING.snapshotSummary,
            validationResultId: null,
            riskLevel: APPROVAL_PENDING.riskLevel,
            requiredApproverRole: APPROVAL_PENDING.requiredApproverRole,
            snapshotAt: APPROVAL_PENDING.snapshotAt,
          }),
        });
      },
    );
    await page.route(`**/v1/workspaces/${TEST_WORKSPACE}/ai-actions/*`, async (route) => {
      await route.fulfill({ json: AI_ACTION });
    });

    await loginAs(page, { roles: ['marketing-director'] });
    await page.goto(`/workspaces/${TEST_WORKSPACE}/ai-actions`);
    await expect(page.locator('[data-testid="ai-action-log-page-empty"]')).toBeVisible();
    await expect(page).toHaveScreenshot('dmos-ai-action-empty.png');
  });
});
