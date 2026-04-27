import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  // Mock workspace and onboarding APIs so the shell resolves immediately
  // without requiring a running backend.
  await page.route('**/api/workspaces', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  );
  await page.route('**/api/workspaces/**', (route) =>
    route.fulfill({ status: 404, body: 'Not found' })
  );
  await page.route('**/api/onboarding/status', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ completed: true }),
    })
  );

  await page.addInitScript(() => {
    try {
      localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
      localStorage.setItem('E2E_SIMPLE_PAGES', '1');
      localStorage.setItem('onboarding_complete', '"true"');
      (window as unknown as { __E2E_TEST_NO_POINTER_BLOCK?: boolean }).__E2E_TEST_NO_POINTER_BLOCK = true;
    } catch {
      // no-op for storage-restricted environments
    }
  });
});

test('lifecycle route renders requirement and governance surfaces', async ({ page }) => {
  await page.goto('/p/proj-1/lifecycle', { waitUntil: 'networkidle' });

  await expect(page.getByTestId('lifecycle-insights-section')).toBeVisible();
  await expect(page.getByTestId('lifecycle-requirements-card')).toBeVisible();
  await expect(page.getByTestId('requirement-lifecycle-board')).toBeVisible();
  await expect(page.getByTestId('lifecycle-approval-card')).toBeVisible();
  await expect(page.getByTestId('approval-detail')).toBeVisible();
});

test('lifecycle approvals and retry flow update visible state', async ({ page }) => {
  await page.goto('/p/proj-1/lifecycle', { waitUntil: 'networkidle' });

  await page.getByRole('button', { name: 'Open Details' }).first().click();
  await expect(page.getByTestId('approval-detail')).toBeVisible();

  await page.getByRole('button', { name: 'Approve' }).first().click();
  await expect(page.getByText('APPROVED').first()).toBeVisible();

  const agentCard = page.getByTestId('lifecycle-agent-run-visibility-card');
  await expect(agentCard).toBeVisible();
  await agentCard.getByRole('button', { name: 'Retry' }).first().click();
  await expect(agentCard.getByText('QUEUED').first()).toBeVisible();

  await expect(page.getByTestId('lifecycle-audit-timeline-card')).toBeVisible();
  await expect(page.getByTestId('audit-timeline')).toBeVisible();
});
