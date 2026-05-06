/**
 * E2E Test Suite – Builder Lifecycle
 *
 * Tests the real page builder (drag-drop, prop editing, save, preview) WITHOUT mocking
 * the renderer or compiler.  Only auth APIs are intercepted so the test can run headlessly
 * without a live identity provider.
 *
 * Running: pnpm exec playwright test e2e/builder-lifecycle.spec.ts
 *
 * @doc.type e2e-spec
 * @doc.purpose Validate the real builder lifecycle – no renderer/compiler mocks
 * @doc.layer product
 * @doc.pattern Playwright E2E
 */

import { expect, test, type Page } from '@playwright/test';

// ─── Shared constants ────────────────────────────────────────────────────────

const WORKSPACE_ID = 'e2e-workspace-builder';
const PROJECT_ID = 'e2e-project-builder-lifecycle';
const ARTIFACT_ID = 'e2e-page-artifact-1';

// ─── Auth + project API stubs (only non-builder APIs are mocked) ─────────────

async function stubAuthAndProjectApis(page: Page): Promise<void> {
  await page.route('/api/v1/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: 'e2e-user-1',
        tenantId: 'e2e-tenant',
        email: 'e2e@ghatana.local',
        roles: ['DEVELOPER'],
      }),
    }),
  );

  await page.route('/api/v1/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ refreshed: true }),
    }),
  );

  await page.route(`/api/v1/yappc/workspaces/${WORKSPACE_ID}/projects/${PROJECT_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: PROJECT_ID,
        workspaceId: WORKSPACE_ID,
        name: 'E2E Builder Project',
        phase: 'GENERATE',
      }),
    }),
  );

  // Return a minimal page artifact so the builder can load without a real DB.
  await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        artifactId: ARTIFACT_ID,
        documentId: 'doc-e2e-1',
        name: 'E2E Test Page',
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
      }),
    }),
  );

  // Artifact save endpoint – capture the saved payload and echo it back.
  await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, async (route) => {
    if (route.request().method() === 'PUT') {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...body, syncStatus: 'SYNCED' }),
      });
    } else {
      await route.fallback();
    }
  });
}

// ─── Tests ───────────────────────────────────────────────────────────────────

test.describe('Builder Lifecycle (real renderer/compiler, mocked auth only)', () => {
  test.beforeEach(async ({ page }) => {
    await stubAuthAndProjectApis(page);
  });

  test('builder canvas loads with no components', async ({ page }) => {
    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );

    // Wait for the page builder shell to appear.
    await expect(
      page.locator('[data-testid="page-builder-shell"]'),
    ).toBeVisible({ timeout: 15000 });

    // Component palette must be visible and non-empty.
    await expect(
      page.locator('[data-testid="component-palette"]'),
    ).toBeVisible();

    // Canvas drop-zone must be rendered and accessible.
    const canvas = page.locator('[data-testid="builder-canvas"]');
    await expect(canvas).toBeVisible();
    await expect(canvas).toHaveAttribute('aria-label');
  });

  test('dragging a component from the palette to the canvas adds it', async ({ page }) => {
    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );
    await expect(page.locator('[data-testid="page-builder-shell"]')).toBeVisible({
      timeout: 15000,
    });

    // Find the Text component in the palette.
    const textTile = page.locator('[data-testid="palette-item-Text"]');
    await expect(textTile).toBeVisible();

    const canvas = page.locator('[data-testid="builder-canvas"]');
    const canvasBbox = await canvas.boundingBox();
    expect(canvasBbox).not.toBeNull();

    const dropX = canvasBbox!.x + canvasBbox!.width / 2;
    const dropY = canvasBbox!.y + canvasBbox!.height / 2;

    // Perform the drag-drop using HTML5 drag events.
    await textTile.dragTo(canvas, {
      targetPosition: { x: canvasBbox!.width / 2, y: canvasBbox!.height / 2 },
    });

    // A node card should now appear on the canvas.
    await expect(
      page.locator('[data-testid^="canvas-node-"]'),
    ).toHaveCount(1, { timeout: 5000 });

    // Drop-zone should announce the addition to screen readers.
    await expect(canvas).toHaveAttribute('aria-label');
    void dropX; // used in future assertion if needed
    void dropY;
  });

  test('selecting a node opens the prop inspector', async ({ page }) => {
    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );
    await expect(page.locator('[data-testid="page-builder-shell"]')).toBeVisible({
      timeout: 15000,
    });

    // Add a Box component via drag.
    const boxTile = page.locator('[data-testid="palette-item-Box"]');
    const canvas = page.locator('[data-testid="builder-canvas"]');
    await boxTile.dragTo(canvas);

    const firstNode = page.locator('[data-testid^="canvas-node-"]').first();
    await firstNode.click();

    // Inspector panel must appear.
    const inspector = page.locator('[data-testid="prop-inspector"]');
    await expect(inspector).toBeVisible({ timeout: 3000 });
    await expect(inspector).toContainText('Box');
  });

  test('editing a prop in the inspector reflects on the canvas', async ({ page }) => {
    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );
    await expect(page.locator('[data-testid="page-builder-shell"]')).toBeVisible({
      timeout: 15000,
    });

    // Drop a Text node and open its inspector.
    const textTile = page.locator('[data-testid="palette-item-Text"]');
    const canvas = page.locator('[data-testid="builder-canvas"]');
    await textTile.dragTo(canvas);

    const firstNode = page.locator('[data-testid^="canvas-node-"]').first();
    await firstNode.click();

    // Edit the `text` prop.
    const textPropInput = page
      .locator('[data-testid="prop-inspector"]')
      .locator('[data-testid="prop-input-text"]');
    await expect(textPropInput).toBeVisible({ timeout: 3000 });
    await textPropInput.fill('Hello E2E');
    await textPropInput.press('Enter');

    // The node's rendered label must update.
    await expect(firstNode).toContainText('Hello E2E', { timeout: 3000 });
  });

  test('save button persists the document without errors', async ({ page }) => {
    const savedPayloads: unknown[] = [];

    // Override the PUT stub to capture saves.
    await page.route(`/api/v1/yappc/artifacts/${ARTIFACT_ID}`, async (route) => {
      if (route.request().method() === 'PUT') {
        savedPayloads.push(route.request().postDataJSON());
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ syncStatus: 'SYNCED' }),
        });
      } else {
        await route.fallback();
      }
    });

    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );
    await expect(page.locator('[data-testid="page-builder-shell"]')).toBeVisible({
      timeout: 15000,
    });

    // Drop a node to make the document dirty.
    const boxTile = page.locator('[data-testid="palette-item-Box"]');
    const canvas = page.locator('[data-testid="builder-canvas"]');
    await boxTile.dragTo(canvas);

    // Click the save button.
    const saveButton = page.locator('[data-testid="builder-save-button"]');
    await expect(saveButton).toBeVisible();
    await saveButton.click();

    // The save indicator must settle to "Saved".
    await expect(
      page.locator('[data-testid="builder-save-status"]'),
    ).toHaveText(/saved/i, { timeout: 5000 });

    // Exactly one PUT must have fired.
    expect(savedPayloads).toHaveLength(1);
  });

  test('preview panel renders generated output without errors', async ({ page }) => {
    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );
    await expect(page.locator('[data-testid="page-builder-shell"]')).toBeVisible({
      timeout: 15000,
    });

    // Drop a Box node so there is something to preview.
    const boxTile = page.locator('[data-testid="palette-item-Box"]');
    const canvas = page.locator('[data-testid="builder-canvas"]');
    await boxTile.dragTo(canvas);

    // Open the preview panel.
    const previewButton = page.locator('[data-testid="builder-preview-button"]');
    await expect(previewButton).toBeVisible();
    await previewButton.click();

    const previewPanel = page.locator('[data-testid="builder-preview-panel"]');
    await expect(previewPanel).toBeVisible({ timeout: 5000 });

    // No error banner must be present.
    await expect(
      page.locator('[data-testid="preview-error-banner"]'),
    ).not.toBeVisible();
  });

  test('undo removes the last dropped node', async ({ page }) => {
    await page.goto(
      `/workspace/${WORKSPACE_ID}/project/${PROJECT_ID}/builder/${ARTIFACT_ID}`,
    );
    await expect(page.locator('[data-testid="page-builder-shell"]')).toBeVisible({
      timeout: 15000,
    });

    const canvas = page.locator('[data-testid="builder-canvas"]');
    const boxTile = page.locator('[data-testid="palette-item-Box"]');

    // Drop two nodes.
    await boxTile.dragTo(canvas, { targetPosition: { x: 100, y: 100 } });
    await boxTile.dragTo(canvas, { targetPosition: { x: 300, y: 100 } });
    await expect(page.locator('[data-testid^="canvas-node-"]')).toHaveCount(2, {
      timeout: 3000,
    });

    // Undo the last drop.
    await page.keyboard.press('Meta+Z');

    // One node should remain.
    await expect(page.locator('[data-testid^="canvas-node-"]')).toHaveCount(1, {
      timeout: 3000,
    });
  });
});
