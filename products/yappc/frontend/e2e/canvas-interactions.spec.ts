import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

/**
 * Component Interaction Tests
 * Tests for cross-component coordination and state synchronization
 */

test.describe('Component Interactions', () => {
  // ============================================
  // TOOLBAR ↔ CANVAS INTERACTIONS
  // ============================================

  test.describe('Toolbar ↔ Canvas Coordination', () => {
    test('should reflect tool selection immediately on canvas', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Select pen tool
      await page.locator('[data-testid="toolbar-tool-pen"]').click();

      // Verify canvas cursor changes
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      const cursor = await canvas.evaluate(
        (el) => window.getComputedStyle(el).cursor
      );

      expect(cursor).toBe('crosshair');

      await teardownTest(page);
    });

    test('should update toolbar when canvas mode changes', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Switch to diagram mode
      await page.locator('[data-testid="mode-diagram"]').click();

      // Verify toolbar updates
      await expect(page.locator('[data-testid="toolbar-mode"]')).toHaveText(
        /Diagram/
      );
      await expect(
        page.locator('[data-testid="toolbar-tool-pen"]')
      ).toBeHidden();

      await teardownTest(page);
    });

    test('should sync undo/redo state between toolbar and canvas', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Undo button initially disabled
      await expect(page.locator('[data-testid="toolbar-undo"]')).toBeDisabled();

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Undo button now enabled
      await expect(page.locator('[data-testid="toolbar-undo"]')).toBeEnabled();

      // Click undo
      await page.locator('[data-testid="toolbar-undo"]').click();

      // Node removed from canvas
      await expect(page.locator('.react-flow__node')).toHaveCount(0);

      // Redo button now enabled
      await expect(page.locator('[data-testid="toolbar-redo"]')).toBeEnabled();

      await teardownTest(page);
    });

    test('should update zoom display when canvas zoom changes', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      const initialZoom = await page
        .locator('[data-testid="toolbar-zoom-level"]')
        .textContent();

      // Zoom with mouse wheel
      await page
        .locator('[data-testid="react-flow-wrapper"]')
        .evaluate((el) => {
          el.dispatchEvent(
            new WheelEvent('wheel', { deltaY: -100, ctrlKey: true })
          );
        });

      await page.waitForTimeout(300);

      const finalZoom = await page
        .locator('[data-testid="toolbar-zoom-level"]')
        .textContent();

      expect(finalZoom).not.toBe(initialZoom);

      await teardownTest(page);
    });

    test('should reflect canvas selection in toolbar context', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Select node
      await page.locator('.react-flow__node').click();

      // Toolbar shows selection count
      await expect(
        page.locator('[data-testid="toolbar-selection-count"]')
      ).toHaveText('1 selected');

      // Toolbar alignment tools enabled
      await expect(
        page.locator('[data-testid="toolbar-align-left"]')
      ).toBeEnabled();

      await teardownTest(page);
    });

    test('should disable toolbar actions when canvas is read-only', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      // Toolbar editing tools should be disabled
      await expect(
        page.locator('[data-testid="toolbar-tool-pen"]')
      ).toBeDisabled();
      await expect(
        page.locator('[data-testid="toolbar-tool-rectangle"]')
      ).toBeDisabled();

      await teardownTest(page);
    });
  });

  // ============================================
  // PANEL ↔ CANVAS INTERACTIONS
  // ============================================

  test.describe('Properties Panel ↔ Canvas Coordination', () => {
    test('should update canvas when properties panel changes', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add and select node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });
      await page.locator('.react-flow__node').click();

      // Change label in properties panel
      await page.locator('[data-testid="property-label"]').fill('Updated API');
      await page.keyboard.press('Enter');

      // Verify canvas updates
      await expect(page.locator('.react-flow__node')).toHaveText(/Updated API/);

      await teardownTest(page);
    });

    test('should update properties panel when canvas selection changes', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add two nodes
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });
      await page
        .locator('[data-testid="palette-item-data"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 500, y: 300 },
        });

      // Select first node
      await page.locator('.react-flow__node').first().click();

      const firstNodeLabel = await page
        .locator('[data-testid="property-label"]')
        .inputValue();

      // Select second node
      await page.locator('.react-flow__node').nth(1).click();

      const secondNodeLabel = await page
        .locator('[data-testid="property-label"]')
        .inputValue();

      expect(secondNodeLabel).not.toBe(firstNodeLabel);

      await teardownTest(page);
    });

    test('should clear properties panel when selection is cleared', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add and select node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });
      await page.locator('.react-flow__node').click();

      // Properties panel shows node properties
      await expect(
        page.locator('[data-testid="properties-panel"]')
      ).toBeVisible();

      // Click canvas background to deselect
      await page
        .locator('[data-testid="react-flow-wrapper"]')
        .click({ position: { x: 100, y: 100 } });

      // Properties panel clears
      await expect(page.locator('[data-testid="property-label"]')).toBeEmpty();

      await teardownTest(page);
    });

    test('should sync color changes between panel and canvas', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add and select node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });
      await page.locator('.react-flow__node').click();

      // Change color in properties panel
      await page.locator('[data-testid="property-color"]').fill('#FF0000');

      // Verify canvas node color updates
      const nodeColor = await page
        .locator('.react-flow__node')
        .evaluate((el) => window.getComputedStyle(el).backgroundColor);

      expect(nodeColor).toBe('rgb(255, 0, 0)');

      await teardownTest(page);
    });

    test('should show multi-select properties when multiple nodes selected', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add two nodes
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });
      await page
        .locator('[data-testid="palette-item-data"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 500, y: 300 },
        });

      // Select both nodes
      await page.keyboard.press('Control+a');

      // Properties panel shows multi-select state
      await expect(
        page.locator('[data-testid="properties-panel-title"]')
      ).toHaveText(/2 items selected/);

      await teardownTest(page);
    });
  });

  test.describe('Left Rail ↔ Canvas Coordination', () => {
    test('should navigate canvas when left rail phase clicked', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Click phase in left rail
      await page.locator('[data-testid="leftrail-phase-2"]').click();

      // Canvas scrolls to phase
      await expect(page).toHaveURL(/#phase-2$/);

      // Phase highlighted on canvas
      await expect(page.locator('[data-phase-id="phase-2"]')).toHaveClass(
        /highlighted/
      );

      await teardownTest(page);
    });

    test('should update left rail when canvas phase focus changes', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Scroll canvas to phase
      await page.locator('[data-phase-id="phase-3"]').scrollIntoViewIfNeeded();

      // Left rail highlights active phase
      await expect(
        page.locator('[data-testid="leftrail-phase-3"]')
      ).toHaveClass(/active/);

      await teardownTest(page);
    });

    test('should sync phase node count between rail and canvas', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      const initialCount = await page
        .locator('[data-testid="leftrail-phase-1-count"]')
        .textContent();

      // Add node to phase 1
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-phase-id="phase-1"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      await page.waitForTimeout(500);

      const finalCount = await page
        .locator('[data-testid="leftrail-phase-1-count"]')
        .textContent();

      expect(parseInt(finalCount!)).toBe(parseInt(initialCount!) + 1);

      await teardownTest(page);
    });
  });

  // ============================================
  // URL ↔ STATE SYNCHRONIZATION
  // ============================================

  test.describe('URL ↔ State Synchronization', () => {
    test('should update URL when canvas mode changes', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Switch to sketch mode
      await page.locator('[data-testid="mode-sketch"]').click();

      // URL updates
      await expect(page).toHaveURL(/mode=sketch/);

      await teardownTest(page);
    });

    test('should restore canvas mode from URL', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas?mode=sketch' });

      // Canvas in sketch mode
      await expect(page.locator('[data-testid="mode-sketch"]')).toHaveClass(
        /active/
      );

      await teardownTest(page);
    });

    test('should update URL when node is selected', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add and select node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });
      await page.locator('.react-flow__node').click();

      // URL includes selected node
      await expect(page).toHaveURL(/selected=node-/);

      await teardownTest(page);
    });

    test('should restore selected node from URL', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas?selected=node-1' });

      // Node is selected
      await expect(page.locator('[data-node-id="node-1"]')).toHaveClass(
        /selected/
      );

      await teardownTest(page);
    });

    test('should update URL when zooming', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Zoom in
      await page.locator('[data-testid="toolbar-zoom-in"]').click();

      // URL includes zoom level
      await expect(page).toHaveURL(/zoom=\d+/);

      await teardownTest(page);
    });

    test('should restore zoom level from URL', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas?zoom=150' });

      // Canvas at 150% zoom
      const zoom = await page
        .locator('[data-testid="toolbar-zoom-level"]')
        .textContent();
      expect(zoom).toBe('150%');

      await teardownTest(page);
    });

    test('should maintain state on browser back/forward', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Switch to sketch mode
      await page.locator('[data-testid="mode-sketch"]').click();
      await page.waitForTimeout(300);

      // Switch to diagram mode
      await page.locator('[data-testid="mode-diagram"]').click();
      await page.waitForTimeout(300);

      // Browser back
      await page.goBack();

      // Sketch mode restored
      await expect(page.locator('[data-testid="mode-sketch"]')).toHaveClass(
        /active/
      );

      // Browser forward
      await page.goForward();

      // Diagram mode restored
      await expect(page.locator('[data-testid="mode-diagram"]')).toHaveClass(
        /active/
      );

      await teardownTest(page);
    });

    test('should sync URL hash with phase focus', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Click phase in left rail
      await page.locator('[data-testid="leftrail-phase-2"]').click();

      // URL hash updates
      await expect(page).toHaveURL(/#phase-2$/);

      // Navigate directly with hash
      await page.goto('/w/ws-1/p/proj-1/canvas#phase-3');

      // Phase 3 is focused
      await expect(
        page.locator('[data-testid="leftrail-phase-3"]')
      ).toHaveClass(/active/);

      await teardownTest(page);
    });
  });

  // ============================================
  // CONTEXT MENU ↔ CANVAS INTERACTIONS
  // ============================================

  test.describe('Context Menu ↔ Canvas Coordination', () => {
    test('should show context menu on right-click', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Right-click node
      await page.locator('.react-flow__node').click({ button: 'right' });

      // Context menu appears
      await expect(page.locator('[data-testid="context-menu"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should execute canvas action from context menu', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Right-click node
      await page.locator('.react-flow__node').click({ button: 'right' });

      // Click delete in context menu
      await page.locator('[data-testid="context-menu-delete"]').click();

      // Node deleted from canvas
      await expect(page.locator('.react-flow__node')).toHaveCount(0);

      await teardownTest(page);
    });

    test('should close context menu on canvas click', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node and show context menu
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });
      await page.locator('.react-flow__node').click({ button: 'right' });

      // Click canvas
      await page
        .locator('[data-testid="react-flow-wrapper"]')
        .click({ position: { x: 500, y: 400 } });

      // Context menu closes
      await expect(page.locator('[data-testid="context-menu"]')).toBeHidden();

      await teardownTest(page);
    });
  });

  // ============================================
  // SEARCH ↔ CANVAS INTERACTIONS
  // ============================================

  test.describe('Search ↔ Canvas Coordination', () => {
    test('should highlight canvas nodes matching search', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add nodes
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Search for node
      await page.locator('[data-testid="canvas-search"]').fill('API');

      // Matching node highlighted
      await expect(page.locator('.react-flow__node')).toHaveClass(
        /search-match/
      );

      await teardownTest(page);
    });

    test('should navigate to search result on click', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Search
      await page.locator('[data-testid="canvas-search"]').fill('node');

      // Click first result
      await page.locator('[data-testid="search-result"]').first().click();

      // Canvas scrolls to node
      await expect(page.locator('.react-flow__node').first()).toBeInViewport();

      await teardownTest(page);
    });

    test('should clear canvas highlights when search cleared', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Search
      await page.locator('[data-testid="canvas-search"]').fill('API');
      await expect(page.locator('.react-flow__node')).toHaveClass(
        /search-match/
      );

      // Clear search
      await page.locator('[data-testid="canvas-search"]').fill('');

      // Highlights removed
      await expect(page.locator('.react-flow__node')).not.toHaveClass(
        /search-match/
      );

      await teardownTest(page);
    });
  });

  // ============================================
  // MINIMAP ↔ CANVAS INTERACTIONS
  // ============================================

  test.describe('Minimap ↔ Canvas Coordination', () => {
    test('should sync minimap viewport with canvas', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Pan canvas
      await page.mouse.move(500, 400);
      await page.mouse.down();
      await page.mouse.move(300, 200, { steps: 10 });
      await page.mouse.up();

      // Minimap viewport updates
      const viewportPosition = await page
        .locator('[data-testid="minimap-viewport"]')
        .evaluate((el) => {
          return {
            x: el.getBoundingClientRect().x,
            y: el.getBoundingClientRect().y,
          };
        });

      expect(viewportPosition.x).toBeGreaterThan(0);

      await teardownTest(page);
    });

    test('should navigate canvas from minimap click', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Click minimap
      await page
        .locator('[data-testid="minimap"]')
        .click({ position: { x: 50, y: 50 } });

      // Canvas viewport updates
      await page.waitForTimeout(300);

      const viewport = await page
        .locator('[data-testid="react-flow-wrapper"]')
        .evaluate((el) => {
          const flow = (window as unknown).reactFlowInstance;
          return flow?.getViewport();
        });

      expect(viewport).toBeDefined();

      await teardownTest(page);
    });
  });
});
