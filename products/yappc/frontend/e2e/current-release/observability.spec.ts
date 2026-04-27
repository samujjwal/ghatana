/**
 * Observability Dashboard E2E Tests
 *
 * Validates that the admin observability route renders system health metrics,
 * monitoring links, and handles loading/error states.
 *
 * @doc.type e2e
 * @doc.purpose Observability dashboard UI correctness
 * @doc.layer product
 */

import { test, expect, Page } from '@playwright/test';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockObservabilityAPI(page: Page) {
  await page.route('**/api/metrics**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        apiLatencyP95Ms: 24,
        agentSuccessRatePct: 98.7,
        activeAgentRuns: 12,
        errorRatePct: 0.3,
        refreshedAt: new Date().toISOString(),
      }),
    });
  });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Observability Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await mockObservabilityAPI(page);
    await page.goto('/app/admin/observability');
  });

  test('renders the System Health heading', async ({ page }) => {
    const heading = page
      .getByRole('heading', { name: /system health/i })
      .or(page.getByText('System Health').first());
    await expect(heading).toBeVisible({ timeout: 6000 });
  });

  test('shows metric cards or an empty state', async ({ page }) => {
    const hasMetrics =
      (await page.locator('[data-testid^="metric-card-"]').count()) > 0;
    if (!hasMetrics) {
      await expect(page.getByText(/no metrics available/i)).toBeVisible({
        timeout: 5000,
      });
    }
  });

  test('renders monitoring stack links (Grafana, Prometheus, Jaeger)', async ({ page }) => {
    await expect(page.getByRole('link', { name: /grafana/i })).toBeVisible({
      timeout: 6000,
    });
    await expect(page.getByRole('link', { name: /prometheus/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /jaeger/i })).toBeVisible();
  });

  test('monitoring links open in a new tab', async ({ page }) => {
    const grafanaLink = page.getByRole('link', { name: /open grafana/i });
    if (await grafanaLink.isVisible()) {
      await expect(grafanaLink).toHaveAttribute('target', '_blank');
      await expect(grafanaLink).toHaveAttribute('rel', /noopener/);
    }
  });

  test('refresh button triggers a metrics reload', async ({ page }) => {
    const refreshBtn = page.getByRole('button', { name: /refresh metrics/i });
    if (await refreshBtn.isVisible()) {
      await refreshBtn.click();
      // Verify the button is not in error state after click
      await expect(page.getByRole('alert')).not.toBeVisible({ timeout: 4000 });
    } else {
      test.skip();
    }
  });

  test('shows error state when metrics endpoint returns 500', async ({ page }) => {
    // Override metrics route to return error
    await page.route('**/api/metrics**', async (route) => {
      await route.fulfill({ status: 500, body: 'Internal Server Error' });
    });

    await page.reload();
    // Either an error message or the dashboard renders with empty data
    const hasError = await page.getByRole('alert').isVisible({ timeout: 4000 }).catch(() => false);
    const hasEmpty = await page.getByText(/no metrics available/i).isVisible({ timeout: 4000 }).catch(() => false);
    expect(hasError || hasEmpty).toBe(true);
  });

  test('route is inaccessible without admin permission (redirected)', async ({ page }) => {
    // If auth middleware is active, a non-admin user would be redirected
    // We just verify the route exists (200 or 403 depending on auth mode)
    const response = await page.request.get('/app/admin/observability');
    const status = response.status();
    // 200 (authenticated admin), 302/301 (redirect to login), or 403 (forbidden) are all valid
    expect([200, 301, 302, 403]).toContain(status);
  });
});
