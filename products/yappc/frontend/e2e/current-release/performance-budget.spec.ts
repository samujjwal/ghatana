/**
 * E2E tests – Performance Budgets
 *
 * Validates that initial route loads are within acceptable time budgets and
 * that lazy-loaded chunks are fetched on demand rather than upfront.
 *
 * NOTE: These tests require a production build (`pnpm build`) and a running
 * preview server. They are intentionally marked `.skip` in CI environments
 * without a built artifact – enable them in release-verification pipelines.
 */

import { test, expect } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:7002';

// Acceptable time-to-interactive thresholds (ms)
const BUDGET = {
  /** Dashboard / landing should be fast (no canvas, no AI). */
  DASHBOARD_TTI: 2500,
  /** Canvas route includes heavy libs — allow more time. */
  CANVAS_TTI: 5000,
  /** Settings route should stay lean. */
  SETTINGS_TTI: 3000,
} as const;

// ---------------------------------------------------------------------------

test.describe('Performance Budgets', () => {
  test.skip('dashboard initial TTI is under budget', async ({ page }) => {
    const start = Date.now();
    await page.goto(`${BASE_URL}/`);
    await page.waitForLoadState('networkidle');
    const elapsed = Date.now() - start;
    expect(elapsed, `Dashboard TTI ${elapsed}ms exceeds ${BUDGET.DASHBOARD_TTI}ms budget`).toBeLessThan(
      BUDGET.DASHBOARD_TTI,
    );
  });

  // -------------------------------------------------------------------------

  test.skip('canvas route TTI is under budget', async ({ page }) => {
    const start = Date.now();
    await page.goto(`${BASE_URL}/p/test-project/canvas`);
    await page.waitForLoadState('networkidle');
    const elapsed = Date.now() - start;
    expect(elapsed, `Canvas TTI ${elapsed}ms exceeds ${BUDGET.CANVAS_TTI}ms budget`).toBeLessThan(
      BUDGET.CANVAS_TTI,
    );
  });

  // -------------------------------------------------------------------------

  test.skip('settings route TTI is under budget', async ({ page }) => {
    const start = Date.now();
    await page.goto(`${BASE_URL}/settings`);
    await page.waitForLoadState('networkidle');
    const elapsed = Date.now() - start;
    expect(elapsed, `Settings TTI ${elapsed}ms exceeds ${BUDGET.SETTINGS_TTI}ms budget`).toBeLessThan(
      BUDGET.SETTINGS_TTI,
    );
  });

  // -------------------------------------------------------------------------

  test.skip('canvas chunk is NOT loaded on dashboard navigation', async ({ page }) => {
    const canvasChunkRequests: string[] = [];

    page.on('request', (req) => {
      const url = req.url();
      if (url.includes('app-canvas') || url.includes('lib-canvas')) {
        canvasChunkRequests.push(url);
      }
    });

    await page.goto(`${BASE_URL}/`);
    await page.waitForLoadState('networkidle');

    expect(canvasChunkRequests).toHaveLength(0);
  });

  // -------------------------------------------------------------------------

  test.skip('canvas chunk IS loaded when navigating to canvas route', async ({ page }) => {
    const canvasChunkRequests: string[] = [];

    page.on('request', (req) => {
      const url = req.url();
      if (url.includes('app-canvas') || url.includes('lib-canvas')) {
        canvasChunkRequests.push(url);
      }
    });

    await page.goto(`${BASE_URL}/p/test-project/canvas`);
    await page.waitForLoadState('networkidle');

    expect(canvasChunkRequests.length).toBeGreaterThan(0);
  });

  // -------------------------------------------------------------------------

  test.skip('Lighthouse performance score ≥ 80 on dashboard', async ({ page }) => {
    // This test is a placeholder for Lighthouse CI integration.
    // Run: `pnpm dlx @lhci/cli autorun` in CI against the preview server.
    test.skip(true, 'Lighthouse CI is run via lhci autorun in the release pipeline');
  });
});

// ---------------------------------------------------------------------------
// Bundle budget enforcement (complement to check:bundle-budget script)
// ---------------------------------------------------------------------------

test.describe('Bundle Budget', () => {
  test.skip('initial JS payload is under 250 KB (gzipped estimate)', async ({ page }) => {
    let totalJsBytes = 0;

    page.on('response', async (resp) => {
      const url = resp.url();
      if (url.endsWith('.js') || url.includes('.js?')) {
        try {
          const body = await resp.body();
          totalJsBytes += body.length;
        } catch {
          // Ignore aborted responses
        }
      }
    });

    await page.goto(`${BASE_URL}/`);
    await page.waitForLoadState('networkidle');

    // Estimate: on-wire size is typically ~35% of raw JS (gzip compression ratio)
    const estimatedGzipped = Math.round(totalJsBytes * 0.35);
    const BUDGET_BYTES = 250 * 1024; // 250 KB
    expect(
      estimatedGzipped,
      `Estimated gzipped initial JS ${(estimatedGzipped / 1024).toFixed(0)} KB exceeds 250 KB budget`,
    ).toBeLessThan(BUDGET_BYTES);
  });
});
