/**
 * E2E: Approval queue page user journeys.
 */
import { test, expect } from '@playwright/test';
import {
  loginAs,
  mockDmosApi,
  TEST_WORKSPACE,
  APPROVAL_PENDING,
  APPROVAL_APPROVED,
} from './fixtures';

test.describe('approval queue', () => {
  test.beforeEach(async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page);
    await page.goto(`/workspaces/${TEST_WORKSPACE}/approvals`);
    await page.waitForURL(new RegExp(`/workspaces/${TEST_WORKSPACE}/approvals$`));
  });

  test('renders the approval queue page', async ({ page }) => {
    await expect(page.locator('[data-testid="approval-queue-page"]')).toBeVisible();
  });

  test('shows both approval rows from mock data', async ({ page }) => {
    await expect(
      page.locator(`[data-testid="approval-row-${APPROVAL_PENDING.requestId}"]`),
    ).toBeVisible();
    await expect(
      page.locator(`[data-testid="approval-row-${APPROVAL_APPROVED.requestId}"]`),
    ).toBeVisible();
  });

  test('shows PENDING status badge', async ({ page }) => {
    await expect(
      page.locator(`[data-testid="status-badge-${APPROVAL_PENDING.requestId}"]`),
    ).toContainText('PENDING');
  });

  test('shows APPROVED status badge', async ({ page }) => {
    await expect(
      page.locator(`[data-testid="status-badge-${APPROVAL_APPROVED.requestId}"]`),
    ).toContainText('APPROVED');
  });

  test('review link navigates to approval detail', async ({ page }) => {
    await page
      .locator(`[data-testid="review-link-${APPROVAL_PENDING.requestId}"]`)
      .click();
    await expect(page).toHaveURL(
      new RegExp(`/workspaces/${TEST_WORKSPACE}/approvals/${APPROVAL_PENDING.requestId}`),
    );
  });

  test('filters by action type — STRATEGY shows only strategy row', async ({ page }) => {
    await page.locator('[data-testid="filter-type"]').selectOption('STRATEGY');
    await expect(
      page.locator(`[data-testid="approval-row-${APPROVAL_PENDING.requestId}"]`),
    ).toBeVisible();
    await expect(
      page.locator(`[data-testid="approval-row-${APPROVAL_APPROVED.requestId}"]`),
    ).not.toBeVisible();
  });

  test('shows permission-denied banner when user has no approver role', async ({ page }) => {
    // Default login provides no roles — banner must be visible (least-privilege default)
    await expect(
      page.locator('[data-testid="permission-denied-banner"]'),
    ).toBeVisible();
  });
});
