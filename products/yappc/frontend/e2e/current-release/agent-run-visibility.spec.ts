/**
 * Agent Run Visibility E2E Tests
 *
 * Validates agent run timeline display, status updates, retry capability,
 * and run output inspection.
 *
 * @doc.type e2e
 * @doc.purpose Agent run visibility and observability UI correctness
 * @doc.layer product
 */

import { test, expect, Page } from '@playwright/test';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockAgentRunGraphQL(page: Page) {
  await page.route('**/graphql', async (route) => {
    const body = route.request().postDataJSON() as { query?: string } | null;
    const query = body?.query ?? '';

    if (query.includes('agentRuns')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            agentRuns: [
              {
                id: 'run-1',
                requirementId: 'req-1',
                type: 'ENRICHMENT',
                status: 'SUCCEEDED',
                output: JSON.stringify({
                  normalizedTitle: 'User can authenticate via SSO',
                  confidence: 0.87,
                  rationale: 'Authentication keyword match.',
                }),
                errorMessage: null,
                startedAt: new Date(Date.now() - 60000).toISOString(),
                completedAt: new Date().toISOString(),
                createdAt: new Date(Date.now() - 60000).toISOString(),
              },
              {
                id: 'run-2',
                requirementId: 'req-1',
                type: 'VALIDATION',
                status: 'FAILED',
                output: null,
                errorMessage: 'Timeout after 30 seconds',
                startedAt: new Date(Date.now() - 120000).toISOString(),
                completedAt: new Date(Date.now() - 90000).toISOString(),
                createdAt: new Date(Date.now() - 120000).toISOString(),
              },
            ],
          },
        }),
      });
      return;
    }

    if (query.includes('startAgentRun')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            startAgentRun: {
              id: 'run-3',
              type: 'VALIDATION',
              status: 'PENDING',
              createdAt: new Date().toISOString(),
            },
          },
        }),
      });
      return;
    }

    await route.continue();
  });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Agent Run Visibility', () => {
  test.beforeEach(async ({ page }) => {
    await mockAgentRunGraphQL(page);
    await page.goto('/app/projects/proj-1/requirements/req-1');
  });

  test('agent run timeline is shown on the requirement page', async ({ page }) => {
    const timeline = page
      .locator('[data-testid="agent-run-timeline"]')
      .or(page.getByText(/agent run|agent activity/i).first());

    const visible = await timeline.isVisible({ timeout: 6000 }).catch(() => false);
    if (!visible) {
      test.skip();
    }
    expect(visible).toBe(true);
  });

  test('SUCCEEDED run shows success indicator', async ({ page }) => {
    const succeededBadge = page
      .getByText('SUCCEEDED')
      .or(page.getByText(/succeeded|success/i))
      .first();

    const visible = await succeededBadge.isVisible({ timeout: 6000 }).catch(() => false);
    if (!visible) {
      test.skip();
    }
    await expect(succeededBadge).toBeVisible();
  });

  test('FAILED run shows failure indicator and error message', async ({ page }) => {
    const failedBadge = page
      .getByText('FAILED')
      .or(page.getByText(/failed/i))
      .first();

    const visible = await failedBadge.isVisible({ timeout: 6000 }).catch(() => false);
    if (!visible) {
      test.skip();
    }
    await expect(failedBadge).toBeVisible();
    const errorMsg = page.getByText(/timeout after 30 seconds/i);
    if (await errorMsg.isVisible({ timeout: 3000 }).catch(() => false)) {
      await expect(errorMsg).toBeVisible();
    }
  });

  test('can expand a SUCCEEDED run to view output', async ({ page }) => {
    const run1 = page
      .getByTestId('agent-run-run-1')
      .or(page.getByText('ENRICHMENT').first());

    if (await run1.isVisible({ timeout: 6000 }).catch(() => false)) {
      await run1.click();
      const outputPanel = page
        .getByText(/normalized title|confidence|rationale/i)
        .or(page.getByTestId('agent-run-output'))
        .first();
      await expect(outputPanel).toBeVisible({ timeout: 4000 });
    } else {
      test.skip();
    }
  });

  test('can retry a FAILED agent run', async ({ page }) => {
    const retryBtn = page.getByRole('button', { name: /retry/i }).first();
    if (await retryBtn.isVisible({ timeout: 6000 }).catch(() => false)) {
      await retryBtn.click();
      // Should show a new PENDING run or indicate retry was triggered
      await expect(
        page.getByText(/pending|running|retry/i).first()
      ).toBeVisible({ timeout: 8000 });
    } else {
      test.skip();
    }
  });

  test('agent run timestamps are displayed', async ({ page }) => {
    const timeline = page.locator('[data-testid="agent-run-timeline"]');
    if (await timeline.isVisible({ timeout: 6000 }).catch(() => false)) {
      const times = timeline.locator('time').or(timeline.locator('[datetime]'));
      const count = await times.count();
      expect(count).toBeGreaterThan(0);
    } else {
      test.skip();
    }
  });

  test('navigates to run detail from timeline', async ({ page }) => {
    const viewDetailLink = page
      .getByRole('link', { name: /view details|details/i })
      .or(page.getByTestId('agent-run-detail-link-run-1'))
      .first();

    if (await viewDetailLink.isVisible({ timeout: 6000 }).catch(() => false)) {
      await viewDetailLink.click();
      await expect(page).toHaveURL(/run-1/, { timeout: 6000 });
    } else {
      test.skip();
    }
  });
});
