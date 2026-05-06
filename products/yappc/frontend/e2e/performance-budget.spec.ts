/**
 * E2E Test Suite – Performance Budget for Large Canvas / Page Documents
 *
 * Verifies that the page builder and canvas remain responsive under large
 * document payloads.  Thresholds:
 *   • Initial render of a 1 000-node document: ≤ 5 000 ms (cold load)
 *   • Interaction latency (node select): ≤ 300 ms
 *   • JS heap after load: ≤ 256 MB
 *   • No more than 5 consecutive slow frames (>50 ms) during idle
 *
 * Auth and CRUD APIs are stubbed.  The large document is injected via the
 * artifact stub so the real serialisation / deserialization path executes.
 *
 * @doc.type e2e-spec
 * @doc.purpose Performance budget for large canvas and page builder documents
 * @doc.layer product
 * @doc.pattern Playwright Performance
 */

import { expect, test, type Page } from '@playwright/test';

// ─── Thresholds ────────────────────────────────────────────────────────────────

const RENDER_BUDGET_MS = 5_000;
const INTERACTION_BUDGET_MS = 300;
const HEAP_BUDGET_MB = 256;
const SLOW_FRAMES_ALLOWED = 5;
const SLOW_FRAME_MS = 50;

// ─── Fixture helpers ───────────────────────────────────────────────────────────

const TENANT_ID = 'e2e-tenant-perf';
const PROJECT_ID = 'proj-perf-001';
const ARTIFACT_ID = 'art-perf-001';

/** Build a large page document with `nodeCount` components. */
function buildLargeDocument(nodeCount: number): Record<string, unknown> {
  const components = Array.from({ length: nodeCount }, (_, i) => ({
    id: `node-${i}`,
    type: 'Box',
    props: {
      id: `node-${i}`,
      label: `Component ${i}`,
      x: (i % 50) * 120,
      y: Math.floor(i / 50) * 80,
      width: 100,
      height: 60,
      backgroundColor: i % 2 === 0 ? '#f0f4ff' : '#fff8f0',
    },
    children: [],
  }));

  const bindings = Array.from({ length: Math.min(nodeCount - 1, 200) }, (_, i) => ({
    id: `binding-${i}`,
    sourceId: `node-${i}`,
    targetId: `node-${i + 1}`,
    type: 'data',
  }));

  return {
    id: 'doc-perf-001',
    version: 1,
    components,
    bindings,
    layout: { type: 'flex', direction: 'column' },
  };
}

const LARGE_DOCUMENT = buildLargeDocument(1_000);

// ─── API stubs ─────────────────────────────────────────────────────────────────

async function stubPerfApis(page: Page): Promise<void> {
  await page.route('/api/v1/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        user: { id: 'user-perf', email: 'perf@example.com', name: 'Perf User', tenantId: TENANT_ID },
        expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      }),
    })
  );

  await page.route('/api/v1/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ expiresAt: new Date(Date.now() + 3_600_000).toISOString() }),
    })
  );

  await page.route(`/api/v1/projects/${PROJECT_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: PROJECT_ID,
        workspaceId: 'ws-perf-001',
        name: 'Perf Test Project',
        phase: 'CONTEXT',
        createdAt: '2026-01-01T00:00:00Z',
      }),
    })
  );

  await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        artifactId: ARTIFACT_ID,
        documentId: 'doc-perf-001',
        name: 'Perf Page',
        syncStatus: 'SYNCED',
        trustLevel: 'TRUSTED',
        dataClassification: 'INTERNAL',
        source: 'manual',
        residualIslandCount: 0,
        roundTripFidelity: 1.0,
        builderDocument: LARGE_DOCUMENT,
      }),
    })
  );

  await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, (route) => {
    if (route.request().method() === 'PUT') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    }
    return route.continue();
  });
}

// ─── Tests ────────────────────────────────────────────────────────────────────

test.describe('Performance Budget – Large Canvas / Page Documents', () => {
  test.beforeEach(async ({ page }) => {
    await stubPerfApis(page);
  });

  // ── Render time ─────────────────────────────────────────────────────────────

  test(`loads a 1 000-node document within ${RENDER_BUDGET_MS} ms`, async ({ page }) => {
    const t0 = Date.now();

    await page.goto(`/p/${PROJECT_ID}/${ARTIFACT_ID}/builder`, { waitUntil: 'networkidle' });
    await page.waitForSelector('[data-testid="builder-canvas"]', { timeout: 10_000 });

    // Wait until all canvas nodes are rendered
    await page.waitForFunction(
      (count) => document.querySelectorAll('[data-testid^="canvas-node-"]').length >= count,
      LARGE_DOCUMENT.components.length,
      { timeout: RENDER_BUDGET_MS + 2_000 }
    );

    const elapsed = Date.now() - t0;
    expect(
      elapsed,
      `Expected initial render of 1 000-node document to complete within ${RENDER_BUDGET_MS} ms but took ${elapsed} ms`
    ).toBeLessThanOrEqual(RENDER_BUDGET_MS);
  });

  // ── Interaction latency ──────────────────────────────────────────────────────

  test(`node selection responds within ${INTERACTION_BUDGET_MS} ms on a large document`, async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/${ARTIFACT_ID}/builder`, { waitUntil: 'networkidle' });
    await page.waitForSelector('[data-testid="builder-canvas"]', { timeout: 10_000 });

    // Pick the first node
    const firstNode = page.locator('[data-testid^="canvas-node-"]').first();
    await firstNode.waitFor({ state: 'visible', timeout: 10_000 });

    // Measure selection latency via a PerformanceMark
    await page.evaluate(() => {
      window.performance.mark('selection-start');
    });

    await firstNode.click();

    // Wait for prop-inspector (which appears after selection)
    const t0 = Date.now();
    await page.waitForSelector('[data-testid="prop-inspector"]', { timeout: INTERACTION_BUDGET_MS + 500 });
    const elapsed = Date.now() - t0;

    expect(
      elapsed,
      `Node selection should show prop-inspector within ${INTERACTION_BUDGET_MS} ms but took ${elapsed} ms`
    ).toBeLessThanOrEqual(INTERACTION_BUDGET_MS);
  });

  // ── Memory footprint ─────────────────────────────────────────────────────────

  test(`JS heap stays below ${HEAP_BUDGET_MB} MB after loading large document`, async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/${ARTIFACT_ID}/builder`, { waitUntil: 'networkidle' });
    await page.waitForSelector('[data-testid="builder-canvas"]', { timeout: 10_000 });

    const metrics = await page.metrics();
    const heapMB = (metrics['JSHeapUsedSize'] as number) / (1024 * 1024);

    expect(
      heapMB,
      `Expected JS heap to be below ${HEAP_BUDGET_MB} MB but was ${heapMB.toFixed(1)} MB`
    ).toBeLessThan(HEAP_BUDGET_MB);
  });

  // ── Slow frame detection ─────────────────────────────────────────────────────

  test(`fewer than ${SLOW_FRAMES_ALLOWED + 1} consecutive slow frames during idle on large document`, async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/${ARTIFACT_ID}/builder`, { waitUntil: 'networkidle' });
    await page.waitForSelector('[data-testid="builder-canvas"]', { timeout: 10_000 });

    // Collect frame durations via requestAnimationFrame loop for 2 seconds
    const slowFrameCount = await page.evaluate(
      ({ durationMs, slowMs }: { durationMs: number; slowMs: number }): Promise<number> => {
        return new Promise((resolve) => {
          let slow = 0;
          let last = performance.now();
          const deadline = last + durationMs;

          function frame(now: number) {
            const duration = now - last;
            if (duration > slowMs) slow++;
            last = now;
            if (now < deadline) {
              requestAnimationFrame(frame);
            } else {
              resolve(slow);
            }
          }

          requestAnimationFrame(frame);
        });
      },
      { durationMs: 2_000, slowMs: SLOW_FRAME_MS }
    );

    expect(
      slowFrameCount,
      `Expected fewer than ${SLOW_FRAMES_ALLOWED + 1} slow frames (>${SLOW_FRAME_MS} ms) but found ${slowFrameCount}`
    ).toBeLessThanOrEqual(SLOW_FRAMES_ALLOWED);
  });

  // ── Scroll performance ────────────────────────────────────────────────────────

  test('canvas scrolls without jank on a 1 000-node document', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/${ARTIFACT_ID}/builder`, { waitUntil: 'networkidle' });
    await page.waitForSelector('[data-testid="builder-canvas"]', { timeout: 10_000 });

    const canvas = page.locator('[data-testid="builder-canvas"]');
    const box = await canvas.boundingBox();

    if (!box) {
      test.skip();
      return;
    }

    // Scroll down the canvas via mouse-wheel and measure wall time
    const t0 = Date.now();
    for (let i = 0; i < 5; i++) {
      await page.mouse.wheel(0, 400);
      await page.waitForTimeout(50); // let the browser process
    }
    const elapsed = Date.now() - t0;

    // 5 scroll events over ~250 ms polling — expect total wall time ≤ 2 000 ms
    expect(
      elapsed,
      `Canvas scroll (5 wheel events) should complete within 2 000 ms but took ${elapsed} ms`
    ).toBeLessThanOrEqual(2_000);
  });
});
