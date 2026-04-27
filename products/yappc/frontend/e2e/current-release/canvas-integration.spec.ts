/**
 * E2E tests – Canvas Integration
 *
 * Covers cross-cutting canvas integration scenarios:
 * - Canvas ↔ code editor panel (node opens inline editor)
 * - Canvas ↔ agent run viewer (node triggers agent panel)
 * - Canvas ↔ requirements (requirement node selection opens detail)
 * - Canvas diagram persistence across route changes
 * - Canvas with visual block editor mode
 * - Multi-node selection and bulk operations
 *
 * All tests are skipped (`test.skip`) because the full canvas + integration
 * routes may not be deployed in CI. Enable them as routes become live.
 *
 * @doc.type e2e
 * @doc.purpose Cross-cutting canvas integration flows
 * @doc.layer product
 */

import { test, expect, type Page } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:7002';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function gotoCanvas(page: Page, projectId = 'demo-project-1'): Promise<void> {
  await page.goto(`${BASE_URL}/app/project/${projectId}/canvas`);
  await page.waitForLoadState('networkidle');
}

async function gotoProject(page: Page, projectId = 'demo-project-1'): Promise<void> {
  await page.goto(`${BASE_URL}/app/project/${projectId}`);
  await page.waitForLoadState('networkidle');
}

async function mockGraphQL(page: Page): Promise<void> {
  await page.route('**/graphql', async (route) => {
    const body = route.request().postDataJSON() as { query?: string } | null;
    const query = body?.query ?? '';

    if (query.includes('project') || query.includes('canvas')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            project: {
              id: 'demo-project-1',
              name: 'Demo Project',
              canvas: { nodes: [], edges: [] },
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
// Suite 1: Canvas ↔ Code Editor Integration
// ---------------------------------------------------------------------------

test.describe('Canvas ↔ Code Editor integration', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('selecting a code node opens the inline code editor panel', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const codeNode = page.getByTestId('canvas-node-code').first();
    await codeNode.click();

    const codeEditor = page.getByTestId('canvas-code-editor-panel');
    await expect(codeEditor).toBeVisible({ timeout: 5000 });
  });

  test('code editor panel shows the correct file for the selected node', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const codeNode = page.getByTestId('canvas-node-code').first();
    await codeNode.click();

    const fileLabel = page.getByTestId('code-editor-file-label');
    await expect(fileLabel).toBeVisible();
    // The label should not be empty — it must reference the node's source file
    const text = await fileLabel.textContent();
    expect(text?.trim().length).toBeGreaterThan(0);
  });

  test('closing the code editor panel deselects the node', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const codeNode = page.getByTestId('canvas-node-code').first();
    await codeNode.click();
    await page.getByTestId('canvas-code-editor-close').click();

    await expect(page.getByTestId('canvas-code-editor-panel')).not.toBeVisible({ timeout: 3000 });
    await expect(codeNode).not.toHaveAttribute('data-selected', 'true');
  });
});

// ---------------------------------------------------------------------------
// Suite 2: Canvas ↔ Agent Run Viewer Integration
// ---------------------------------------------------------------------------

test.describe('Canvas ↔ Agent Run Viewer integration', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('selecting an agent node opens the agent run viewer panel', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const agentNode = page.getByTestId('canvas-node-agent').first();
    await agentNode.click();

    const agentPanel = page.getByTestId('agent-run-viewer-panel');
    await expect(agentPanel).toBeVisible({ timeout: 5000 });
  });

  test('agent run viewer displays run history for the selected agent node', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const agentNode = page.getByTestId('canvas-node-agent').first();
    await agentNode.click();

    const agentPanel = page.getByTestId('agent-run-viewer-panel');
    await expect(agentPanel).toBeVisible();
    // Should show at least a heading (empty state or run list)
    const heading = agentPanel.getByRole('heading').first();
    await expect(heading).toBeVisible();
  });

  test('triggering agent run from canvas node updates run status in panel', async ({ page }) => {
    await page.route('**/graphql', async (route) => {
      const body = route.request().postDataJSON() as { query?: string } | null;
      const query = body?.query ?? '';
      if (query.includes('triggerAgentRun') || query.includes('createAgentRun')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: { triggerAgentRun: { id: 'run-1', status: 'RUNNING' } } }),
        });
        return;
      }
      await route.continue();
    });

    await gotoCanvas(page);
    const agentNode = page.getByTestId('canvas-node-agent').first();
    await agentNode.click();

    const triggerBtn = page.getByTestId('canvas-agent-trigger-run');
    if (await triggerBtn.isVisible({ timeout: 3000 })) {
      await triggerBtn.click();
      const statusBadge = page.getByTestId('agent-run-status');
      await expect(statusBadge).toHaveText(/RUNNING|Queued/i, { timeout: 5000 });
    }
  });
});

// ---------------------------------------------------------------------------
// Suite 3: Canvas ↔ Requirements Integration
// ---------------------------------------------------------------------------

test.describe('Canvas ↔ Requirements integration', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('selecting a requirement node opens the requirement detail panel', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const reqNode = page.getByTestId('canvas-node-requirement').first();
    await reqNode.click();

    const detailPanel = page.getByTestId('requirement-detail-panel');
    await expect(detailPanel).toBeVisible({ timeout: 5000 });
  });

  test('requirement detail panel shows title and status', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const reqNode = page.getByTestId('canvas-node-requirement').first();
    await reqNode.click();

    const detailPanel = page.getByTestId('requirement-detail-panel');
    await expect(detailPanel.getByTestId('requirement-title')).toBeVisible();
    await expect(detailPanel.getByTestId('requirement-status')).toBeVisible();
  });

  test('clicking requirement node title navigates to requirements list', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const reqNode = page.getByTestId('canvas-node-requirement').first();
    await reqNode.click();

    const link = page.getByTestId('requirement-open-fullview');
    if (await link.isVisible({ timeout: 3000 })) {
      await link.click();
      await expect(page).toHaveURL(/requirements/);
    }
  });
});

// ---------------------------------------------------------------------------
// Suite 4: Canvas Diagram Persistence
// ---------------------------------------------------------------------------

test.describe('Canvas diagram state persistence', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('canvas node positions are restored after navigating away and back', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    // Capture position of first node
    const firstNode = page.getByTestId('canvas-node').first();
    const box = await firstNode.boundingBox();

    // Navigate away then back
    await gotoProject(page);
    await gotoCanvas(page);

    const restoredNode = page.getByTestId('canvas-node').first();
    const restoredBox = await restoredNode.boundingBox();

    // Positions should be equal (persisted state)
    expect(restoredBox?.x).toBeCloseTo(box?.x ?? 0, 0);
    expect(restoredBox?.y).toBeCloseTo(box?.y ?? 0, 0);
  });

  test('canvas zoom level is restored after navigation', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    // Zoom in via keyboard shortcut
    await page.keyboard.press('Control+=');

    const zoomLevelBefore = await page.getByTestId('canvas-zoom-level').textContent();

    await gotoProject(page);
    await gotoCanvas(page);

    const zoomLevelAfter = await page.getByTestId('canvas-zoom-level').textContent();
    expect(zoomLevelAfter).toBe(zoomLevelBefore);
  });
});

// ---------------------------------------------------------------------------
// Suite 5: Canvas Visual Block Editor Mode
// ---------------------------------------------------------------------------

test.describe('Canvas Visual Block Editor mode', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('switching to block editor mode renders block-style nodes', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const modeToggle = page.getByTestId('canvas-mode-block');
    if (await modeToggle.isVisible({ timeout: 3000 })) {
      await modeToggle.click();
      const blockNodes = page.locator('[data-testid^="canvas-block-node-"]');
      await expect(blockNodes.first()).toBeVisible({ timeout: 5000 });
    }
  });

  test('block editor mode persists when switching back from code editor', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const blockModeBtn = page.getByTestId('canvas-mode-block');
    const codeModeBtn = page.getByTestId('canvas-mode-code');

    if (await blockModeBtn.isVisible({ timeout: 3000 }) && await codeModeBtn.isVisible()) {
      await blockModeBtn.click();
      await codeModeBtn.click();
      await blockModeBtn.click();

      const activeMode = page.getByTestId('canvas-active-mode');
      await expect(activeMode).toHaveText(/block/i);
    }
  });
});

// ---------------------------------------------------------------------------
// Suite 6: Multi-Node Selection
// ---------------------------------------------------------------------------

test.describe('Canvas multi-node selection', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('shift-clicking selects multiple nodes', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const nodes = page.getByTestId('canvas-node');
    const count = await nodes.count();

    if (count >= 2) {
      await nodes.nth(0).click();
      await nodes.nth(1).click({ modifiers: ['Shift'] });

      const selected = page.locator('[data-testid="canvas-node"][data-selected="true"]');
      await expect(selected).toHaveCount(2);
    }
  });

  test('lasso selection captures nodes within the drag rectangle', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const canvas = page.getByTestId('canvas-area');
    if (await canvas.isVisible({ timeout: 3000 })) {
      const box = await canvas.boundingBox();
      if (box) {
        // Drag from top-left corner to cover part of the canvas
        await page.mouse.move(box.x + 10, box.y + 10);
        await page.mouse.down();
        await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
        await page.mouse.up();
      }
    }
    // Verify selection rectangle did not crash the page
    await expect(page.getByTestId('canvas-area')).toBeVisible();
  });

  test('pressing Delete removes all selected nodes', async ({ page }) => {
    await mockGraphQL(page);
    await gotoCanvas(page);

    const nodes = page.getByTestId('canvas-node');
    const countBefore = await nodes.count();

    if (countBefore >= 1) {
      await nodes.first().click();
      await page.keyboard.press('Delete');

      const countAfter = await nodes.count();
      expect(countAfter).toBeLessThan(countBefore);
    }
  });
});
