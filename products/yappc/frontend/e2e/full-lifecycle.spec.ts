/**
 * E2E Test Suite – Full Product Lifecycle
 *
 * Covers the critical user journey from login through workspace creation, project creation,
 * and progressing through each phase cockpit (Evolve → Generate → Validate).
 * Auth APIs are mocked; product lifecycle APIs use the real server where available or
 * plausible JSON stubs when the server is not the concern under test.
 *
 * @doc.type e2e-spec
 * @doc.purpose Full end-to-end lifecycle from login to evolve phase cockpit
 * @doc.layer product
 * @doc.pattern Playwright E2E
 */

import { expect, test, type Page } from '@playwright/test';

// ─── Fixture data ─────────────────────────────────────────────────────────────

const TENANT_ID = 'e2e-tenant-lifecycle';
const WORKSPACE_ID = 'ws-lifecycle-001';
const PROJECT_ID = 'proj-lifecycle-001';
const ARTIFACT_ID = 'art-lifecycle-001';

const fixtureWorkspace = {
  id: WORKSPACE_ID,
  tenantId: TENANT_ID,
  name: 'Lifecycle Test Workspace',
  description: 'Created during E2E lifecycle test',
  createdAt: '2026-01-01T00:00:00Z',
};

const fixtureProject = {
  id: PROJECT_ID,
  workspaceId: WORKSPACE_ID,
  name: 'Lifecycle Test Project',
  phase: 'EVOLVE',
  createdAt: '2026-01-01T00:00:00Z',
};

const fixtureArtifact = {
  artifactId: ARTIFACT_ID,
  documentId: 'doc-lifecycle-001',
  name: 'Lifecycle Page',
  syncStatus: 'SYNCED',
  trustLevel: 'TRUSTED',
  dataClassification: 'INTERNAL',
  source: 'manual',
  residualIslandCount: 0,
  roundTripFidelity: 1.0,
  builderDocument: {
    rootNodes: [],
    nodes: {},
    metadata: { dataClassification: 'INTERNAL' },
  },
  validationSummary: { valid: true, errorCount: 0, warningCount: 0 },
  aiChangeRecords: [],
};

// ─── Mock helper ──────────────────────────────────────────────────────────────

async function stubLifecycleApis(page: Page): Promise<void> {
  // Auth
  await page.route('/api/v1/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: 'e2e-user-lifecycle',
        tenantId: TENANT_ID,
        email: 'lifecycle@ghatana.local',
        roles: ['DEVELOPER', 'PRODUCT_OWNER'],
      }),
    }),
  );

  await page.route('/api/v1/auth/refresh', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"refreshed":true}' }),
  );

  // Login endpoint
  await page.route('/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ userId: 'e2e-user-lifecycle', tenantId: TENANT_ID }),
    }),
  );

  // Workspace list + create
  await page.route('/api/v1/yappc/workspaces', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ workspaces: [fixtureWorkspace] }),
      });
    } else if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(fixtureWorkspace),
      });
    } else {
      await route.fallback();
    }
  });

  // Workspace detail
  await page.route(`/api/v1/yappc/workspaces/${WORKSPACE_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(fixtureWorkspace),
    }),
  );

  // Project list + create
  await page.route(`/api/v1/yappc/workspaces/${WORKSPACE_ID}/projects`, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ projects: [fixtureProject] }),
      });
    } else if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(fixtureProject),
      });
    } else {
      await route.fallback();
    }
  });

  // Project detail
  await page.route(
    `/api/v1/yappc/workspaces/${WORKSPACE_ID}/projects/${PROJECT_ID}`,
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(fixtureProject),
      }),
  );

  // Phase cockpit config
  await page.route(
    `/api/v1/yappc/workspaces/${WORKSPACE_ID}/projects/${PROJECT_ID}/phase-config`,
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          currentPhase: 'EVOLVE',
          phases: [
            { id: 'INTENT', label: 'Intent', status: 'COMPLETED' },
            { id: 'SHAPE', label: 'Shape', status: 'COMPLETED' },
            { id: 'VALIDATE', label: 'Validate', status: 'COMPLETED' },
            { id: 'GENERATE', label: 'Generate', status: 'COMPLETED' },
            { id: 'RUN', label: 'Run', status: 'COMPLETED' },
            { id: 'OBSERVE', label: 'Observe', status: 'COMPLETED' },
            { id: 'LEARN', label: 'Learn', status: 'COMPLETED' },
            { id: 'EVOLVE', label: 'Evolve', status: 'IN_PROGRESS' },
          ],
        }),
      }),
  );

  // Phase cockpit readiness + next-action
  await page.route(
    `/api/v1/yappc/workspaces/${WORKSPACE_ID}/projects/${PROJECT_ID}/readiness`,
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ readinessScore: 85, blockers: [] }),
      }),
  );

  // Artifact
  await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(fixtureArtifact),
    }),
  );
}

// ─── Tests ───────────────────────────────────────────────────────────────────

test.describe('Full Product Lifecycle', () => {
  test.beforeEach(async ({ page }) => {
    await stubLifecycleApis(page);
  });

  test('login → workspaces dashboard loads with workspace list', async ({ page }) => {
    // Navigate to login and authenticate.
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', 'lifecycle@ghatana.local');
    await page.fill('[data-testid="password-input"]', 'e2e-password');
    await page.click('[data-testid="login-submit"]');

    // Expect redirect to workspaces dashboard.
    await expect(page).toHaveURL(/\/workspaces/, { timeout: 10000 });
    await expect(page.getByTestId('workspaces-page')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('workspace-card')).toHaveCount(1, { timeout: 5000 });
  });

  test('navigating to a workspace shows project list', async ({ page }) => {
    await page.goto(`/workspaces/${WORKSPACE_ID}`);

    await expect(
      page.locator('[data-testid="projects-list"]'),
    ).toBeVisible({ timeout: 10000 });
    await expect(
      page.locator('[data-testid="project-card"]'),
    ).toHaveCount(1, { timeout: 5000 });
  });

  test('opening a project navigates to the phase cockpit', async ({ page }) => {
    await page.goto(`/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}`);

    const cockpit = page.locator('[data-testid="phase-cockpit"]');
    await expect(cockpit).toBeVisible({ timeout: 15000 });

    // Evolve phase should be the active phase.
    await expect(
      cockpit.locator('[data-testid="phase-tab-EVOLVE"]'),
    ).toHaveAttribute('aria-selected', 'true', { timeout: 5000 });
  });

  test('phase cockpit renders all 8 phase tabs', async ({ page }) => {
    await page.goto(`/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}`);

    const cockpit = page.locator('[data-testid="phase-cockpit"]');
    await expect(cockpit).toBeVisible({ timeout: 15000 });

    const expectedPhases = [
      'INTENT', 'SHAPE', 'VALIDATE', 'GENERATE', 'RUN', 'OBSERVE', 'LEARN', 'EVOLVE',
    ];
    for (const phase of expectedPhases) {
      await expect(
        cockpit.locator(`[data-testid="phase-tab-${phase}"]`),
      ).toBeVisible({ timeout: 3000 });
    }
  });

  test('evolve cockpit panel renders roadmap and retrospective sections', async ({ page }) => {
    await page.goto(`/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/phase/EVOLVE`);

    const evolvePanel = page.locator('[data-testid="evolve-roadmap-panel"]');
    await expect(evolvePanel).toBeVisible({ timeout: 10000 });

    // Panel sections.
    await expect(
      evolvePanel.locator('[data-testid="roadmap-section"]'),
    ).toBeVisible();
    await expect(
      evolvePanel.locator('[data-testid="retrospective-section"]'),
    ).toBeVisible({ timeout: 3000 });
  });

  test('creating a new workspace via the creation dialog succeeds', async ({ page }) => {
    const createdWorkspaces: unknown[] = [];

    await page.route('/api/v1/yappc/workspaces', async (route) => {
      if (route.request().method() === 'POST') {
        createdWorkspaces.push(route.request().postDataJSON());
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(fixtureWorkspace),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ workspaces: [] }),
        });
      }
    });

    await page.goto('/workspaces');
    await expect(page.getByTestId('workspaces-page')).toBeVisible({ timeout: 10000 });

    // Click "New Workspace".
    await page.click('[data-testid="create-workspace-button"]');
    const dialog = page.locator('[data-testid="create-workspace-dialog"]');
    await expect(dialog).toBeVisible({ timeout: 3000 });

    // Fill in workspace details.
    await dialog.locator('[data-testid="workspace-name-input"]').fill('New Lifecycle WS');
    await dialog.locator('[data-testid="create-workspace-submit"]').click();

    // Dialog should close and workspace list should update.
    await expect(dialog).not.toBeVisible({ timeout: 5000 });
    expect(createdWorkspaces).toHaveLength(1);
  });

  test('creating a new project inside a workspace succeeds', async ({ page }) => {
    const createdProjects: unknown[] = [];

    await page.route(
      `/api/v1/yappc/workspaces/${WORKSPACE_ID}/projects`,
      async (route) => {
        if (route.request().method() === 'POST') {
          createdProjects.push(route.request().postDataJSON());
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify(fixtureProject),
          });
        } else {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ projects: [] }),
          });
        }
      },
    );

    await page.goto(`/workspaces/${WORKSPACE_ID}`);
    await expect(page.locator('[data-testid="projects-list"]')).toBeVisible({ timeout: 10000 });

    await page.click('[data-testid="create-project-button"]');
    const dialog = page.locator('[data-testid="create-project-dialog"]');
    await expect(dialog).toBeVisible({ timeout: 3000 });

    await dialog.locator('[data-testid="project-name-input"]').fill('New Lifecycle Project');
    await dialog.locator('[data-testid="create-project-submit"]').click();

    await expect(dialog).not.toBeVisible({ timeout: 5000 });
    expect(createdProjects).toHaveLength(1);
  });
});
