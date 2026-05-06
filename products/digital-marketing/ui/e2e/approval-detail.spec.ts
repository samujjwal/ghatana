/**
 * E2E: Approval detail page user journeys.
 */
import { test, expect } from '@playwright/test';
import {
  loginAs,
  mockDmosApi,
  TEST_WORKSPACE,
  TEST_PRINCIPAL,
  APPROVAL_PENDING,
  APPROVAL_SNAPSHOT,
  navigateInApp,
} from './fixtures';

const DETAIL_URL = `/workspaces/${TEST_WORKSPACE}/approvals/${APPROVAL_PENDING.requestId}`;

test.describe('approval detail', () => {
  test.beforeEach(async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page);
    await navigateInApp(page, DETAIL_URL);
    await expect(page.locator('[data-testid="approval-detail-page"]')).toBeVisible();
  });

  test('shows PENDING status badge', async ({ page }) => {
    await expect(page.locator('[data-testid="approval-status-badge"]')).toContainText('PENDING');
  });

  test('shows snapshot panel with summary and risk level', async ({ page }) => {
    await expect(page.locator('[data-testid="approval-snapshot-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="snapshot-field-summary"]')).toContainText(
      APPROVAL_SNAPSHOT.snapshotSummary,
    );
    await expect(page.locator('[data-testid="approval-risk-level"]')).toContainText(
      String(APPROVAL_SNAPSHOT.riskLevel),
    );
  });

  test('hides decide button and shows permission notice when user has no approver role', async ({ page }) => {
    // Default login provides no roles (least-privilege) — button must be absent
    await expect(page.locator('[data-testid="open-decide-dialog"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="approval-permission-denied"]')).toBeVisible();
  });

  test('shows decide button when user has matching approver role', async ({ page }) => {
    // Login with marketing-director role (matches requiredApproverRole)
    await page.goto('/login');
    await loginAs(page, { roles: ['marketing-director'] });
    await navigateInApp(page, DETAIL_URL);
    await expect(page.locator('[data-testid="open-decide-dialog"]')).toBeVisible();
    await expect(page.locator('[data-testid="approval-permission-denied"]')).not.toBeVisible();
  });

  test('approves approval and shows success', async ({ page }) => {
    await page.goto('/login');
    await loginAs(page, { roles: ['marketing-director'] });
    await navigateInApp(page, DETAIL_URL);
    await page.click('[data-testid="open-decide-dialog"]');
    await page.locator('[data-testid="decision-approve"]').click();
    await page.fill('[data-testid="decision-comment"]', 'Approved for Q3 strategy');
    await page.click('[data-testid="decision-submit"]');
    await expect(page.locator('[data-testid="approval-status-badge"]')).toContainText('APPROVED');
    await expect(page.locator('[data-testid="approval-permission-denied"]')).not.toBeVisible();
  });

  test('rejects approval and shows success', async ({ page }) => {
    await page.goto('/login');
    await loginAs(page, { roles: ['marketing-director'] });
    await navigateInApp(page, DETAIL_URL);
    await page.click('[data-testid="open-decide-dialog"]');
    await page.locator('[data-testid="decision-reject"]').click();
    await page.fill('[data-testid="decision-comment"]', 'Needs more detail on budget');
    await page.click('[data-testid="decision-submit"]');
    await expect(page.locator('[data-testid="approval-status-badge"]')).toContainText('REJECTED');
  });

  test('requires comment for high-risk approvals', async ({ page }) => {
    await page.goto('/login');
    await loginAs(page, { roles: ['marketing-director'] });
    // Mock a high-risk approval
    await page.route(
      `**/v1/workspaces/${TEST_WORKSPACE}/approvals/${APPROVAL_PENDING.requestId}`,
      (route) => route.fulfill({ json: { ...APPROVAL_PENDING, riskLevel: 5 } }),
    );
    await navigateInApp(page, DETAIL_URL);
    await page.click('[data-testid="open-decide-dialog"]');
    await page.locator('[data-testid="decision-approve"]').click();
    await page.click('[data-testid="decision-submit"]');
    await expect(page.locator('[data-testid="decision-error"]')).toContainText('comment');
  });

  test('shows error state when API returns error @a11y', async ({ page }) => {
    const errorDetailUrl = `/workspaces/${TEST_WORKSPACE}/approvals/req-e2e-error`;
    await page.route(
      `**/v1/workspaces/${TEST_WORKSPACE}/approvals/req-e2e-error`,
      (route) => route.fulfill({ status: 500, body: 'Internal Server Error' }),
    );
    await navigateInApp(page, errorDetailUrl);
    await expect(page.locator('[data-testid="approval-detail-error"]')).toBeVisible();
  });

  test('shows 403 error when unauthorized decision attempt', async ({ page }) => {
    await page.goto('/login');
    await loginAs(page, { roles: ['marketing-director'] });
    await navigateInApp(page, DETAIL_URL);
    await page.click('[data-testid="open-decide-dialog"]');
    // Mock 403 response from backend
    await page.route(
      `**/v1/workspaces/${TEST_WORKSPACE}/approvals/${APPROVAL_PENDING.requestId}/decide`,
      (route) => route.fulfill({ status: 403, body: 'Forbidden' }),
    );
    await page.locator('[data-testid="decision-approve"]').click();
    await page.fill('[data-testid="decision-comment"]', 'Should fail');
    await page.click('[data-testid="decision-submit"]');
    await expect(page.locator('[data-testid="decision-error"]')).toContainText('403');
  });
});
