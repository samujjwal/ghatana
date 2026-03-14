import { test, expect } from '@playwright/test';
import { mockCollectionsAPI, mockWorkflowsAPI, mockAlertsAPI } from './helpers/api-mocks';

/**
 * Visual Regression Tests — Data-Cloud UI
 *
 * These tests capture full-page and component-level screenshots and compare
 * them against committed baseline images generated on the first run.
 *
 * HOW IT WORKS
 * ------------
 * 1. First run (baseline generation):
 *      npx playwright test e2e/visual-regression.spec.ts --update-snapshots
 *    Playwright writes PNG files to e2e/__snapshots__/visual-regression.spec.ts-snapshots/
 *    Commit these baseline images together with the code change.
 *
 * 2. Subsequent CI runs:
 *      npx playwright test e2e/visual-regression.spec.ts
 *    Playwright captures fresh screenshots and compares pixel-by-pixel against
 *    the committed baselines. Tests fail if any pixel differs beyond the
 *    configured `maxDiffPixelRatio` threshold (default: 0.01 = 1%).
 *
 * HOW TO UPDATE BASELINES (after intentional UI change)
 * -------------------------------------------------------
 *    npx playwright test e2e/visual-regression.spec.ts --update-snapshots
 *    git add e2e/__snapshots__ && git commit -m "chore: update visual baselines"
 *
 * SCOPE
 * -----
 * Tests are scoped to Chromium only (canonical) to avoid cross-browser
 * rendering differences producing false negatives. Firefox / Safari functional
 * coverage is provided by other spec files.
 *
 * @doc.type test
 * @doc.purpose Visual regression snapshot tests for all major UI pages
 * @doc.layer testing
 */

// Shared screenshot options: 1% pixel-diff tolerance, 8 px antialiasing threshold
const SCREENSHOT_OPTS: Parameters<typeof expect.soft>[0] extends never
  ? never
  : { maxDiffPixelRatio: number; threshold: number } = {
  maxDiffPixelRatio: 0.01,  // fail if >1% of pixels differ
  threshold: 0.2,           // per-pixel colour distance [0–1]; ignore minor antialiasing
};

// All visual tests run on Desktop Chrome only (canonical baseline browser)
test.use({ browserName: 'chromium' });

// ─────────────────────────────────────────────────────────────────────────────
// Helper: mask dynamic content (timestamps, random IDs) before snapshotting
// ─────────────────────────────────────────────────────────────────────────────
async function maskDynamicContent(page: import('@playwright/test').Page) {
  // Mask elements that change on every load (timestamps, counters, IDs)
  await page.addStyleTag({
    content: `
      [data-testid="timestamp"],
      [data-testid="relative-time"],
      [data-testid="entity-id"],
      [class*="timestamp"],
      [class*="updatedAt"],
      [class*="createdAt"],
      time {
        visibility: hidden !important;
      }
    `,
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: wait for page to be visually stable (no pending network/animations)
// ─────────────────────────────────────────────────────────────────────────────
async function waitForPageStable(page: import('@playwright/test').Page) {
  // Wait for network to be idle (no outstanding requests for 500 ms)
  await page.waitForLoadState('networkidle');
  // Disable CSS animations/transitions so screenshots are deterministic
  await page.addStyleTag({
    content: `
      *, *::before, *::after {
        animation-duration: 0s !important;
        animation-delay: 0s !important;
        transition-duration: 0s !important;
        transition-delay: 0s !important;
      }
    `,
  });
}

// =============================================================================
//  DASHBOARD
// =============================================================================

test.describe('Visual Regression — Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await mockCollectionsAPI(page);
    await page.goto('/');
    await waitForPageStable(page);
    await maskDynamicContent(page);
  });

  test('dashboard full page — desktop (1280×800)', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('dashboard-desktop.png', SCREENSHOT_OPTS);
  });

  test('dashboard full page — tablet (768×1024)', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await expect(page).toHaveScreenshot('dashboard-tablet.png', SCREENSHOT_OPTS);
  });

  test('dashboard full page — mobile (375×667)', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(page).toHaveScreenshot('dashboard-mobile.png', SCREENSHOT_OPTS);
  });

  test('dashboard KPI cards section', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    const kpiSection = page.locator('[data-testid="kpi-cards"], .kpi-cards, [class*="KPICard"]').first();
    if (await kpiSection.isVisible().catch(() => false)) {
      await expect(kpiSection).toHaveScreenshot('dashboard-kpi-cards.png', SCREENSHOT_OPTS);
    } else {
      // Fallback: capture top 400 px of the page (where KPIs typically live)
      await expect(page).toHaveScreenshot('dashboard-kpi-fallback.png', {
        ...SCREENSHOT_OPTS,
        clip: { x: 0, y: 0, width: 1280, height: 400 },
      });
    }
  });
});

// =============================================================================
//  COLLECTIONS PAGE
// =============================================================================

test.describe('Visual Regression — Collections', () => {
  test.beforeEach(async ({ page }) => {
    await mockCollectionsAPI(page);
    await page.goto('/collections');
    await waitForPageStable(page);
    await maskDynamicContent(page);
  });

  test('collections list — desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('collections-list-desktop.png', SCREENSHOT_OPTS);
  });

  test('collections list — mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(page).toHaveScreenshot('collections-list-mobile.png', SCREENSHOT_OPTS);
  });

  test('collections empty state', async ({ page }) => {
    // Override the mock to return an empty list
    await page.route('**/api/v1/collections', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items: [], total: 0, page: 1, pageSize: 10, hasMore: false }),
        });
      } else {
        route.continue();
      }
    });
    await page.goto('/collections');
    await waitForPageStable(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('collections-empty-state.png', SCREENSHOT_OPTS);
  });
});

// =============================================================================
//  WORKFLOWS PAGE
// =============================================================================

test.describe('Visual Regression — Workflows', () => {
  test.beforeEach(async ({ page }) => {
    await mockWorkflowsAPI(page);
    await page.goto('/pipelines');
    await waitForPageStable(page);
    await maskDynamicContent(page);
  });

  test('workflows list — desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('workflows-list-desktop.png', SCREENSHOT_OPTS);
  });

  test('workflows list — mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(page).toHaveScreenshot('workflows-list-mobile.png', SCREENSHOT_OPTS);
  });
});

// =============================================================================
//  ALERTS PAGE
// =============================================================================

test.describe('Visual Regression — Alerts', () => {
  test.beforeEach(async ({ page }) => {
    await mockAlertsAPI(page);
    await page.goto('/alerts');
    await waitForPageStable(page);
    await maskDynamicContent(page);
  });

  test('alerts page — desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('alerts-desktop.png', SCREENSHOT_OPTS);
  });

  test('alerts page — no active alerts (all clear)', async ({ page }) => {
    await page.route('**/api/v1/alerts*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], total: 0 }),
      });
    });
    await page.goto('/alerts');
    await waitForPageStable(page);
    await maskDynamicContent(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('alerts-empty-state.png', SCREENSHOT_OPTS);
  });
});

// =============================================================================
//  SETTINGS PAGE
// =============================================================================

test.describe('Visual Regression — Settings', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/settings');
    await waitForPageStable(page);
  });

  test('settings page — desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('settings-desktop.png', SCREENSHOT_OPTS);
  });

  test('settings page — mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(page).toHaveScreenshot('settings-mobile.png', SCREENSHOT_OPTS);
  });
});

// =============================================================================
//  GOVERNANCE / TRUST CENTER
// =============================================================================

test.describe('Visual Regression — Governance / Trust Center', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/trust');
    await waitForPageStable(page);
    await maskDynamicContent(page);
  });

  test('trust center — desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('trust-center-desktop.png', SCREENSHOT_OPTS);
  });
});

// =============================================================================
//  PLUGINS PAGE
// =============================================================================

test.describe('Visual Regression — Plugins', () => {
  test.beforeEach(async ({ page }) => {
    // Route plugin list API to a stable mock
    await page.route('**/api/v1/plugins*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { id: 'plugin-1', name: 'PostgreSQL Connector', status: 'active', version: '1.0.0', description: 'Postgres storage backend' },
            { id: 'plugin-2', name: 'Kafka Stream', status: 'active', version: '2.3.0', description: 'High-throughput event streaming' },
            { id: 'plugin-3', name: 'Redis Cache', status: 'inactive', version: '1.5.0', description: 'In-memory L2 caching' },
          ],
          total: 3,
        }),
      });
    });
    await page.goto('/plugins');
    await waitForPageStable(page);
    await maskDynamicContent(page);
  });

  test('plugins page — desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('plugins-desktop.png', SCREENSHOT_OPTS);
  });
});

// =============================================================================
//  404 NOT FOUND PAGE
// =============================================================================

test.describe('Visual Regression — Error / 404 Page', () => {
  test('404 page renders correctly', async ({ page }) => {
    await page.goto('/this-route-does-not-exist-abc123');
    await waitForPageStable(page);
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page).toHaveScreenshot('not-found-page.png', SCREENSHOT_OPTS);
  });
});

// =============================================================================
//  COMPONENT-LEVEL SNAPSHOTS (sidebar navigation, header)
// =============================================================================

test.describe('Visual Regression — Layout Components', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForPageStable(page);
  });

  test('sidebar navigation', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    // Attempt to target the sidebar/nav element specifically
    const sidebar = page.locator(
      '[data-testid="sidebar"], nav[aria-label], [class*="Sidebar"], [class*="sidenav"], aside'
    ).first();
    if (await sidebar.isVisible().catch(() => false)) {
      await expect(sidebar).toHaveScreenshot('sidebar-nav.png', SCREENSHOT_OPTS);
    }
  });

  test('page header / topbar', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    const header = page.locator(
      'header, [data-testid="topbar"], [data-testid="header"], [class*="Header"], [class*="Topbar"]'
    ).first();
    if (await header.isVisible().catch(() => false)) {
      await expect(header).toHaveScreenshot('page-header.png', SCREENSHOT_OPTS);
    }
  });
});
