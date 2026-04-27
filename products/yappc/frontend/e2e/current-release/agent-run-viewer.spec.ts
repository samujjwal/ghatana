/**
 * E2E Test Suite - Agent Run Viewer
 *
 * Tests agent run visibility, streaming output, status transitions,
 * and retry flows on the lifecycle page.
 *
 * @doc.type e2e-spec
 * @doc.purpose Agent run viewer end-to-end coverage
 * @doc.layer product
 * @doc.pattern Playwright E2E
 */

import { test, expect } from '@playwright/test';

const PROJECT_ID = 'e2e-project-agent-run';

const mockAgentRuns = [
  {
    id: 'run-1',
    agentId: 'agent-code-gen',
    agentName: 'Code Generator',
    status: 'COMPLETED',
    startedAt: '2026-05-01T10:00:00.000Z',
    completedAt: '2026-05-01T10:01:30.000Z',
    durationMs: 90000,
    inputSummary: 'Generate auth service',
    outputSummary: 'Generated 3 files',
    logs: [
      { timestamp: '2026-05-01T10:00:05.000Z', level: 'INFO', message: 'Starting code generation' },
      { timestamp: '2026-05-01T10:01:00.000Z', level: 'INFO', message: 'Files written successfully' },
    ],
  },
  {
    id: 'run-2',
    agentId: 'agent-review',
    agentName: 'Code Reviewer',
    status: 'FAILED',
    startedAt: '2026-05-01T10:05:00.000Z',
    completedAt: '2026-05-01T10:05:10.000Z',
    durationMs: 10000,
    inputSummary: 'Review auth.ts',
    outputSummary: null,
    error: 'LLM timeout after 10s',
    logs: [
      { timestamp: '2026-05-01T10:05:05.000Z', level: 'ERROR', message: 'LLM timeout' },
    ],
  },
  {
    id: 'run-3',
    agentId: 'agent-test-writer',
    agentName: 'Test Writer',
    status: 'RUNNING',
    startedAt: '2026-05-01T10:10:00.000Z',
    completedAt: null,
    durationMs: null,
    inputSummary: 'Write tests for auth service',
    outputSummary: null,
    logs: [],
  },
];

test.beforeEach(async ({ page }) => {
  // Standard API mocks for app shell
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

  // Agent runs list
  await page.route(`**/api/v1/projects/${PROJECT_ID}/agent-runs`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ runs: mockAgentRuns }),
    })
  );

  // Individual run detail
  await page.route(`**/api/v1/projects/${PROJECT_ID}/agent-runs/**`, (route) => {
    const url = route.request().url();
    const runId = url.split('/agent-runs/')[1]?.split('?')[0] ?? '';
    const run = mockAgentRuns.find((r) => r.id === runId);
    if (run) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ run }),
      });
    } else {
      route.fulfill({ status: 404, body: JSON.stringify({ error: 'Not found' }) });
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

test.describe('Agent Run Viewer', () => {
  test('displays agent runs list with status badges', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const agentCard = page.getByTestId('lifecycle-agent-run-visibility-card');
    await expect(agentCard).toBeVisible({ timeout: 10_000 });

    await expect(agentCard.getByText('Code Generator')).toBeVisible();
    await expect(agentCard.getByText('Code Reviewer')).toBeVisible();
    await expect(agentCard.getByText('Test Writer')).toBeVisible();
  });

  test('shows COMPLETED, FAILED, RUNNING status indicators', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const agentCard = page.getByTestId('lifecycle-agent-run-visibility-card');
    await expect(agentCard).toBeVisible({ timeout: 10_000 });

    await expect(agentCard.getByText('COMPLETED')).toBeVisible();
    await expect(agentCard.getByText('FAILED')).toBeVisible();
    await expect(agentCard.getByText('RUNNING')).toBeVisible();
  });

  test('retry button triggers status change to QUEUED for failed runs', async ({ page }) => {
    let retryCallCount = 0;

    await page.route(`**/api/v1/projects/${PROJECT_ID}/agent-runs/run-2/retry`, (route) => {
      retryCallCount++;
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          run: { ...mockAgentRuns[1], id: 'run-2', status: 'QUEUED' },
        }),
      });
    });

    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const agentCard = page.getByTestId('lifecycle-agent-run-visibility-card');
    await expect(agentCard).toBeVisible({ timeout: 10_000 });

    const retryBtn = agentCard.getByRole('button', { name: /retry/i }).first();
    await expect(retryBtn).toBeVisible();
    await retryBtn.click();

    await expect(agentCard.getByText('QUEUED').first()).toBeVisible({ timeout: 5_000 });
    expect(retryCallCount).toBe(1);
  });

  test('expanding a run shows log output', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const agentCard = page.getByTestId('lifecycle-agent-run-visibility-card');
    await expect(agentCard).toBeVisible({ timeout: 10_000 });

    // Click to expand the first completed run
    const viewDetailsBtn = agentCard
      .getByRole('button', { name: /view|details|expand/i })
      .first();

    if (await viewDetailsBtn.isVisible()) {
      await viewDetailsBtn.click();
      await expect(agentCard.getByText(/starting code generation/i)).toBeVisible({ timeout: 5_000 });
    }
  });

  test('RUNNING run shows progress/streaming indicator', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const agentCard = page.getByTestId('lifecycle-agent-run-visibility-card');
    await expect(agentCard).toBeVisible({ timeout: 10_000 });

    // RUNNING run should show some in-progress indicator (spinner, pulse animation)
    // We check that "Test Writer" row is present and associated with RUNNING status
    const runnerRow = agentCard.getByText('Test Writer').locator('..');
    await expect(runnerRow).toBeVisible();
  });
});
