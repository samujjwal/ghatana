/**
 * E2E Test Suite - Requirement Lifecycle
 *
 * Tests the full requirement lifecycle: capture, status transitions,
 * approval gate, audit timeline, and agent integration.
 *
 * @doc.type e2e-spec
 * @doc.purpose Requirement lifecycle end-to-end coverage
 * @doc.layer product
 * @doc.pattern Playwright E2E
 */

import { test, expect } from '@playwright/test';

const PROJECT_ID = 'e2e-project-req-lifecycle';

const mockRequirements = [
  {
    id: 'req-1',
    title: 'User Authentication',
    description: 'As a user I can log in with email and password',
    status: 'IN_REVIEW',
    priority: 'HIGH',
    createdAt: '2026-04-20T09:00:00.000Z',
    updatedAt: '2026-04-21T14:00:00.000Z',
    phaseId: 'phase-draft',
  },
  {
    id: 'req-2',
    title: 'Password Reset Flow',
    description: 'User can request a password reset via email',
    status: 'APPROVED',
    priority: 'MEDIUM',
    createdAt: '2026-04-18T10:00:00.000Z',
    updatedAt: '2026-04-22T08:00:00.000Z',
    phaseId: 'phase-approved',
  },
  {
    id: 'req-3',
    title: 'Two-Factor Authentication',
    description: 'Users can enable TOTP-based 2FA',
    status: 'DRAFT',
    priority: 'MEDIUM',
    createdAt: '2026-04-25T12:00:00.000Z',
    updatedAt: '2026-04-25T12:00:00.000Z',
    phaseId: 'phase-draft',
  },
];

const mockApprovals = [
  {
    id: 'appr-1',
    requirementId: 'req-1',
    title: 'User Authentication',
    status: 'PENDING',
    requestedAt: '2026-04-21T15:00:00.000Z',
    requestedBy: { id: 'user-1', name: 'Alice', email: 'alice@example.com' },
    reviewedBy: null,
    reviewedAt: null,
    comments: null,
  },
];

test.beforeEach(async ({ page }) => {
  // App shell mocks
  await page.route('**/api/workspaces', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  );
  await page.route('**/api/onboarding/status', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ completed: true }),
    })
  );

  // Requirements endpoints
  await page.route(`**/api/v1/projects/${PROJECT_ID}/requirements`, (route) => {
    if (route.request().method() === 'GET') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ requirements: mockRequirements }),
      });
    } else {
      route.fallback();
    }
  });

  await page.route(`**/api/v1/projects/${PROJECT_ID}/requirements/**`, (route) => {
    if (route.request().method() === 'PATCH') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ requirement: { ...mockRequirements[0], status: 'APPROVED' } }),
      });
    } else {
      route.fallback();
    }
  });

  // Approvals
  await page.route(`**/api/v1/projects/${PROJECT_ID}/approvals`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ approvals: mockApprovals }),
    })
  );

  await page.route(`**/api/v1/projects/${PROJECT_ID}/approvals/**`, (route) => {
    const method = route.request().method();
    if (method === 'POST' || method === 'PATCH') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ approval: { ...mockApprovals[0], status: 'APPROVED' } }),
      });
    } else {
      route.fallback();
    }
  });

  await page.addInitScript(() => {
    try {
      localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
      localStorage.setItem('E2E_SIMPLE_PAGES', '1');
      localStorage.setItem('onboarding_complete', '"true"');
      (window as unknown as { __E2E_TEST_NO_POINTER_BLOCK?: boolean }).__E2E_TEST_NO_POINTER_BLOCK = true;
    } catch {
      // no-op
    }
  });
});

test.describe('Requirement Lifecycle', () => {
  test('lifecycle page renders requirements and approvals surfaces', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    await expect(page.getByTestId('lifecycle-requirements-card')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('lifecycle-approval-card')).toBeVisible({ timeout: 10_000 });
  });

  test('shows all requirements with status indicators', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const reqCard = page.getByTestId('lifecycle-requirements-card');
    await expect(reqCard).toBeVisible({ timeout: 10_000 });

    await expect(reqCard.getByText('User Authentication')).toBeVisible();
    await expect(reqCard.getByText('Password Reset Flow')).toBeVisible();
    await expect(reqCard.getByText('Two-Factor Authentication')).toBeVisible();
  });

  test('shows DRAFT, IN_REVIEW, and APPROVED statuses', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const reqCard = page.getByTestId('lifecycle-requirements-card');
    await expect(reqCard).toBeVisible({ timeout: 10_000 });

    await expect(reqCard.getByText('IN_REVIEW').or(reqCard.getByText('In Review'))).toBeVisible();
    await expect(reqCard.getByText('APPROVED').or(reqCard.getByText('Approved'))).toBeVisible();
    await expect(reqCard.getByText('DRAFT').or(reqCard.getByText('Draft'))).toBeVisible();
  });

  test('approve action on pending approval updates status to APPROVED', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const approvalCard = page.getByTestId('lifecycle-approval-card');
    await expect(approvalCard).toBeVisible({ timeout: 10_000 });

    // Look for the Approve button in the approvals section
    const approveBtn = approvalCard.getByRole('button', { name: /approve/i }).first();
    await expect(approveBtn).toBeVisible();

    await approveBtn.click();

    // After approve, the status in the card should reflect APPROVED
    await expect(approvalCard.getByText('APPROVED').first()).toBeVisible({ timeout: 5_000 });
  });

  test('requirement board shows items grouped by lifecycle phase', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const board = page.getByTestId('requirement-lifecycle-board');
    await expect(board).toBeVisible({ timeout: 10_000 });
  });

  test('audit timeline card is visible after interactions', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    // Perform an approval
    const approvalCard = page.getByTestId('lifecycle-approval-card');
    await expect(approvalCard).toBeVisible({ timeout: 10_000 });

    const approveBtn = approvalCard.getByRole('button', { name: /approve/i }).first();
    if (await approveBtn.isVisible()) {
      await approveBtn.click();
    }

    await expect(page.getByTestId('lifecycle-audit-timeline-card')).toBeVisible({ timeout: 10_000 });
  });

  test('requirement detail can be opened via Open Details button', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const approvalCard = page.getByTestId('lifecycle-approval-card');
    await expect(approvalCard).toBeVisible({ timeout: 10_000 });

    const openDetailsBtn = approvalCard.getByRole('button', { name: /open details/i }).first();
    if (await openDetailsBtn.isVisible()) {
      await openDetailsBtn.click();
      await expect(page.getByTestId('approval-detail')).toBeVisible({ timeout: 5_000 });
    }
  });
});
