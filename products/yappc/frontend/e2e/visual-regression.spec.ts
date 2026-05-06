/**
 * E2E Test Suite – Visual Regression for Phase Cockpits
 *
 * Takes Playwright screenshot snapshots of each phase cockpit panel and asserts
 * they match the baseline using `toHaveScreenshot()`.
 *
 * Phases covered:
 *   Intent / Context / Plan / Execute / Verify / Observe / Learn / Evolve (Institutionalize)
 *
 * First-run baseline generation:
 *   pnpm exec playwright test visual-regression.spec.ts --update-snapshots
 *
 * Subsequent CI runs compare against the committed baseline.
 *
 * Auth and project APIs are stubbed to avoid real-network latency.
 * The snapshots capture only the phase cockpit container so navigation chrome
 * is excluded from the diff.
 *
 * @doc.type e2e-spec
 * @doc.purpose Visual regression for each phase cockpit state
 * @doc.layer product
 * @doc.pattern Playwright Visual Comparison
 */

import { expect, test, type Page } from '@playwright/test';

// ─── Fixture data ─────────────────────────────────────────────────────────────

const TENANT_ID = 'e2e-tenant-visual';
const WORKSPACE_ID = 'ws-visual-001';
const PROJECT_ID = 'proj-visual-001';
const ARTIFACT_ID = 'art-visual-001';

const fixtureWorkspace = {
  id: WORKSPACE_ID,
  tenantId: TENANT_ID,
  name: 'Visual Regression Workspace',
  description: 'Visual regression test workspace',
  createdAt: '2026-01-01T00:00:00Z',
};

const fixtureProject = {
  id: PROJECT_ID,
  workspaceId: WORKSPACE_ID,
  name: 'Visual Test Project',
  phase: 'INTENT',
  createdAt: '2026-01-01T00:00:00Z',
};

const fixtureArtifact = {
  artifactId: ARTIFACT_ID,
  documentId: 'doc-visual-001',
  name: 'Visual Page',
  syncStatus: 'SYNCED',
  trustLevel: 'TRUSTED',
  dataClassification: 'INTERNAL',
  source: 'manual',
  residualIslandCount: 0,
  roundTripFidelity: 1.0,
  builderDocument: {
    id: 'doc-visual-001',
    version: 1,
    components: [],
    bindings: [],
    layout: { type: 'flex', direction: 'column' },
  },
};

type PhaseRoute =
  | 'intent'
  | 'context'
  | 'plan'
  | 'execute'
  | 'verify'
  | 'observe'
  | 'learn'
  | 'evolve';

const PHASE_TAB_LABELS: Record<PhaseRoute, string> = {
  intent: 'Intent',
  context: 'Context',
  plan: 'Plan',
  execute: 'Execute',
  verify: 'Verify',
  observe: 'Observe',
  learn: 'Learn',
  evolve: 'Evolve',
};

// ─── API stubs ─────────────────────────────────────────────────────────────────

async function stubVisualApis(page: Page): Promise<void> {
  // Auth
  await page.route('/api/v1/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        user: { id: 'user-visual', email: 'visual@example.com', name: 'Visual User', tenantId: TENANT_ID },
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

  // Workspaces
  await page.route('/api/v1/workspaces', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [fixtureWorkspace], total: 1 }),
    })
  );

  await page.route(`/api/v1/workspaces/${WORKSPACE_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(fixtureWorkspace),
    })
  );

  // Projects
  await page.route(`/api/v1/workspaces/${WORKSPACE_ID}/projects`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [fixtureProject], total: 1 }),
    })
  );

  await page.route(`/api/v1/projects/${PROJECT_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(fixtureProject),
    })
  );

  // Phase config + readiness
  await page.route(`/api/v1/projects/${PROJECT_ID}/phase-config`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        currentPhase: 'INTENT',
        availablePhases: ['INTENT', 'CONTEXT', 'PLAN', 'EXECUTE', 'VERIFY', 'OBSERVE', 'LEARN', 'EVOLVE'],
        phaseReadiness: {
          INTENT: { ready: true, completedSteps: 0, totalSteps: 3, blockers: [] },
          CONTEXT: { ready: false, completedSteps: 0, totalSteps: 4, blockers: [] },
          PLAN: { ready: false, completedSteps: 0, totalSteps: 3, blockers: [] },
          EXECUTE: { ready: false, completedSteps: 0, totalSteps: 3, blockers: [] },
          VERIFY: { ready: false, completedSteps: 0, totalSteps: 3, blockers: [] },
          OBSERVE: { ready: false, completedSteps: 0, totalSteps: 3, blockers: [] },
          LEARN: { ready: false, completedSteps: 0, totalSteps: 3, blockers: [] },
          EVOLVE: { ready: false, completedSteps: 0, totalSteps: 3, blockers: [] },
        },
      }),
    })
  );

  await page.route(`/api/v1/projects/${PROJECT_ID}/readiness`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ ready: true, blockers: [] }),
    })
  );

  // Artifact
  await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(fixtureArtifact),
    })
  );
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Navigate to the project cockpit and wait for the phase tab bar to appear.
 */
async function navigateToCockpit(page: Page): Promise<void> {
  await page.goto(`/p/${PROJECT_ID}`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="phase-tab-bar"]', { timeout: 10_000 });
}

/**
 * Click a phase tab and wait for its panel to stabilise.
 */
async function selectPhaseTab(page: Page, phase: PhaseRoute): Promise<void> {
  const label = PHASE_TAB_LABELS[phase];
  await page.click(`[data-testid="phase-tab-bar"] button:has-text("${label}")`);
  await page.waitForSelector(`[data-testid="phase-cockpit-panel"]`, { timeout: 8_000 });
  // Wait for any skeleton/loading indicators to clear
  await page.waitForFunction(
    () => document.querySelectorAll('[data-testid="panel-skeleton"]').length === 0
  );
}

/**
 * Take a screenshot of only the phase cockpit panel element.
 */
async function screenshotCockpit(page: Page, snapshotName: string): Promise<void> {
  const cockpitEl = page.locator('[data-testid="phase-cockpit-panel"]');
  await expect(cockpitEl).toHaveScreenshot(snapshotName, {
    maxDiffPixelRatio: 0.02, // 2% pixel diff tolerance for font/AA differences
    animations: 'disabled',
  });
}

// ─── Tests ────────────────────────────────────────────────────────────────────

test.describe('Visual Regression – Phase Cockpits', () => {
  test.beforeEach(async ({ page }) => {
    await stubVisualApis(page);
  });

  // Each phase cockpit renders within its own tab panel.
  // `toHaveScreenshot` saves/loads from e2e/__snapshots__/visual-regression.spec.ts-snapshots/

  test('Intent phase cockpit matches baseline', async ({ page }) => {
    await navigateToCockpit(page);
    await selectPhaseTab(page, 'intent');
    await screenshotCockpit(page, 'phase-intent.png');
  });

  test('Context phase cockpit matches baseline', async ({ page }) => {
    await navigateToCockpit(page);
    await selectPhaseTab(page, 'context');
    await screenshotCockpit(page, 'phase-context.png');
  });

  test('Plan phase cockpit matches baseline', async ({ page }) => {
    await navigateToCockpit(page);
    await selectPhaseTab(page, 'plan');
    await screenshotCockpit(page, 'phase-plan.png');
  });

  test('Execute phase cockpit matches baseline', async ({ page }) => {
    await navigateToCockpit(page);
    await selectPhaseTab(page, 'execute');
    await screenshotCockpit(page, 'phase-execute.png');
  });

  test('Verify phase cockpit matches baseline', async ({ page }) => {
    await navigateToCockpit(page);
    await selectPhaseTab(page, 'verify');
    await screenshotCockpit(page, 'phase-verify.png');
  });

  test('Observe phase cockpit matches baseline', async ({ page }) => {
    await navigateToCockpit(page);
    await selectPhaseTab(page, 'observe');
    await screenshotCockpit(page, 'phase-observe.png');
  });

  test('Learn phase cockpit matches baseline', async ({ page }) => {
    await navigateToCockpit(page);
    await selectPhaseTab(page, 'learn');
    await screenshotCockpit(page, 'phase-learn.png');
  });

  test('Evolve phase cockpit matches baseline', async ({ page }) => {
    await navigateToCockpit(page);
    await selectPhaseTab(page, 'evolve');
    await screenshotCockpit(page, 'phase-evolve.png');
  });
});
