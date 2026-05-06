/**
 * E2E Test Suite – Accessibility Regression
 *
 * Runs @axe-core/playwright checks against major routes/flows.
 * Tests are scoped to WCAG 2.1 AA level (the minimum contractual requirement).
 * Additional manual assertions cover keyboard navigation and focus management.
 *
 * @doc.type e2e-spec
 * @doc.purpose Accessibility regression scanning for major routes
 * @doc.layer product
 * @doc.pattern Playwright E2E
 */

import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// ─── Constants ────────────────────────────────────────────────────────────────

const WORKSPACE_ID = 'e2e-workspace-a11y';
const PROJECT_ID = 'e2e-project-a11y';
const ARTIFACT_ID = 'e2e-artifact-a11y';

// ─── Route stubs ──────────────────────────────────────────────────────────────

async function stubApis(page: Page): Promise<void> {
  await page.route('/api/v1/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: 'e2e-user-a11y',
        tenantId: 'e2e-tenant',
        email: 'a11y@ghatana.local',
        roles: ['DEVELOPER'],
      }),
    }),
  );

  await page.route('/api/v1/yappc/workspaces', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        workspaces: [
          {
            id: WORKSPACE_ID,
            tenantId: 'e2e-tenant',
            name: 'A11y Test Workspace',
            createdAt: '2026-01-01T00:00:00Z',
          },
        ],
      }),
    }),
  );

  await page.route(`/api/v1/yappc/workspaces/${WORKSPACE_ID}/projects`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        projects: [
          {
            id: PROJECT_ID,
            workspaceId: WORKSPACE_ID,
            name: 'A11y Test Project',
            phase: 'EVOLVE',
          },
        ],
      }),
    }),
  );

  await page.route(
    `/api/v1/yappc/workspaces/${WORKSPACE_ID}/projects/${PROJECT_ID}`,
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: PROJECT_ID,
          workspaceId: WORKSPACE_ID,
          name: 'A11y Test Project',
          phase: 'EVOLVE',
        }),
      }),
  );

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

  await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        artifactId: ARTIFACT_ID,
        documentId: 'doc-a11y-001',
        name: 'A11y Test Page',
        syncStatus: 'SYNCED',
        trustLevel: 'TRUSTED',
        dataClassification: 'INTERNAL',
        source: 'manual',
        residualIslandCount: 0,
        builderDocument: { rootNodes: [], nodes: {}, metadata: {} },
        validationSummary: { valid: true, errorCount: 0, warningCount: 0 },
        aiChangeRecords: [],
      }),
    }),
  );
}

// ─── Helper: run axe and assert zero WCAG 2.1 AA violations ──────────────────

async function assertNoA11yViolations(page: Page, context?: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();

  // Custom failure message with context so engineers can locate the violation.
  expect(
    results.violations,
    `Accessibility violations on "${context ?? page.url()}": ${
      results.violations
        .map((v) => `[${v.id}] ${v.description} — ${v.nodes.map((n) => n.target.join(' > ')).join(', ')}`)
        .join('\n')
    }`,
  ).toHaveLength(0);
}

// ─── Tests ───────────────────────────────────────────────────────────────────

test.describe('Accessibility regression (WCAG 2.1 AA)', () => {
  test.beforeEach(async ({ page }) => {
    await stubApis(page);
  });

  test('login page has no axe violations', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('[data-testid="email-input"]')).toBeVisible({
      timeout: 10000,
    });
    await assertNoA11yViolations(page, 'Login page');
  });

  test('workspaces dashboard has no axe violations', async ({ page }) => {
    await page.goto('/workspaces');
    await expect(page.getByTestId('workspaces-page')).toBeVisible({ timeout: 10000 });
    await assertNoA11yViolations(page, 'Workspaces dashboard');
  });

  test('project phase cockpit has no axe violations', async ({ page }) => {
    await page.goto(`/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}`);
    await expect(page.locator('[data-testid="phase-cockpit"]')).toBeVisible({
      timeout: 15000,
    });
    await assertNoA11yViolations(page, 'Phase cockpit');
  });

  test('page builder has no axe violations', async ({ page }) => {
    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );
    await expect(page.locator('[data-testid="page-builder-shell"]')).toBeVisible({
      timeout: 15000,
    });
    await assertNoA11yViolations(page, 'Page builder');
  });

  // ─── Keyboard navigation ───────────────────────────────────────────────────

  test('login form can be completed using keyboard only', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('[data-testid="email-input"]')).toBeVisible({ timeout: 10000 });

    // Tab to email, fill, tab to password, fill, tab to submit, press Enter.
    await page.keyboard.press('Tab');
    await page.keyboard.type('a11y@ghatana.local');
    await page.keyboard.press('Tab');
    await page.keyboard.type('password');
    await page.keyboard.press('Tab');

    // The submit button must have focus (no focus traps before it).
    const submitButton = page.locator('[data-testid="login-submit"]');
    await expect(submitButton).toBeFocused({ timeout: 2000 });
    await page.keyboard.press('Enter');
  });

  test('phase tab bar is navigable via arrow keys', async ({ page }) => {
    await page.goto(`/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}`);
    const cockpit = page.locator('[data-testid="phase-cockpit"]');
    await expect(cockpit).toBeVisible({ timeout: 15000 });

    // Focus the first tab and navigate forward.
    const firstTab = cockpit.locator('[role="tab"]').first();
    await firstTab.focus();
    await expect(firstTab).toBeFocused({ timeout: 2000 });

    await page.keyboard.press('ArrowRight');
    const secondTab = cockpit.locator('[role="tab"]').nth(1);
    await expect(secondTab).toBeFocused({ timeout: 2000 });
  });

  test('modal dialog traps focus within itself', async ({ page }) => {
    await page.goto('/workspaces');
    await expect(page.getByTestId('workspaces-page')).toBeVisible({ timeout: 10000 });

    const createButton = page.locator('[data-testid="create-workspace-button"]');
    if (!(await createButton.isVisible())) {
      test.skip();
      return;
    }

    await createButton.click();
    const dialog = page.locator('[data-testid="create-workspace-dialog"]');
    await expect(dialog).toBeVisible({ timeout: 3000 });

    // Tab through all interactive elements in the dialog.  Focus must not leave.
    for (let i = 0; i < 5; i++) {
      await page.keyboard.press('Tab');
      const focusedElement = page.locator(':focus');
      // All focused elements must be descendants of the dialog.
      await expect(dialog.locator(':focus')).toHaveCount(1, { timeout: 1000 });
      void focusedElement;
    }

    // Escape must close the dialog and restore focus to the trigger.
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible({ timeout: 3000 });
    await expect(createButton).toBeFocused({ timeout: 2000 });
  });

  // ─── ARIA live region announcements ───────────────────────────────────────

  test('save success in builder announces via a live region', async ({ page }) => {
    await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, async (route) => {
      if (route.request().method() === 'PUT') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ syncStatus: 'SYNCED' }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            artifactId: ARTIFACT_ID,
            documentId: 'doc-a11y-001',
            name: 'A11y Test Page',
            syncStatus: 'SYNCED',
            trustLevel: 'TRUSTED',
            dataClassification: 'INTERNAL',
            source: 'manual',
            residualIslandCount: 0,
            builderDocument: { rootNodes: [], nodes: {}, metadata: {} },
            validationSummary: { valid: true, errorCount: 0, warningCount: 0 },
            aiChangeRecords: [],
          }),
        });
      }
    });

    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );
    await expect(page.locator('[data-testid="page-builder-shell"]')).toBeVisible({
      timeout: 15000,
    });

    // Drop a node to dirty the document.
    const boxTile = page.locator('[data-testid="palette-item-Box"]');
    const canvas = page.locator('[data-testid="builder-canvas"]');
    await boxTile.dragTo(canvas);

    // Save.
    await page.click('[data-testid="builder-save-button"]');

    // A live region or status area must announce the save.
    const announcer = page.locator('[aria-live="polite"], [aria-live="assertive"]');
    await expect(announcer.first()).toBeVisible({ timeout: 5000 });
  });
});
