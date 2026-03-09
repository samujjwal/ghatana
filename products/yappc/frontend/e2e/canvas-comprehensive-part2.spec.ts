import { test, expect, type Page } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

/**
 * Canvas Comprehensive Tests - Part 2
 * Operations, Tools, Alignment, Layers, Export, Collaboration
 */

test.describe('Canvas - Operations & Tools', () => {
  // ============================================
  // SECTION 3: OPERATIONS (30 tests)
  // ============================================

  test.describe('Node Operations', () => {
    test('should add all 7 node types', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      const nodeTypes = [
        'api',
        'data',
        'component',
        'service',
        'page',
        'frame',
        'group',
      ];

      for (let i = 0; i < nodeTypes.length; i++) {
        const type = nodeTypes[i];
        await page
          .locator(`[data-testid="palette-item-${type}"]`)
          .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
            targetPosition: { x: 100 + i * 100, y: 200 },
          });
      }

      // Verify all 7 nodes added
      await expect(page.locator('.react-flow__node')).toHaveCount(7);

      // Verify each type rendered correctly
      for (const type of nodeTypes) {
        await expect(page.locator(`[data-node-type="${type}"]`)).toBeVisible();
      }

      await teardownTest(page);
    });

    test('should select single node', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Click node
      await page.locator('.react-flow__node').click();

      // Verify selection highlight
      await expect(page.locator('.react-flow__node.selected')).toHaveCount(1);

      // Verify properties panel updates
      await expect(
        page.locator('[data-testid="node-properties-panel"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should select multiple nodes with Shift+Click', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add two nodes
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 200, y: 200 },
        });
      await page
        .locator('[data-testid="palette-item-data"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 400, y: 200 },
        });

      // Click first node
      await page.locator('.react-flow__node').first().click();

      // Shift+Click second node
      await page
        .locator('.react-flow__node')
        .last()
        .click({ modifiers: ['Shift'] });

      // Verify both selected
      await expect(page.locator('.react-flow__node.selected')).toHaveCount(2);

      await teardownTest(page);
    });

    test('should select multiple nodes with drag selection', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add three nodes
      for (let i = 0; i < 3; i++) {
        await page
          .locator('[data-testid="palette-item-api"]')
          .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
            targetPosition: { x: 200 + i * 100, y: 200 },
          });
      }

      // Drag selection rectangle around all nodes
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await canvas.hover({ position: { x: 150, y: 150 } });
      await page.mouse.down();
      await page.mouse.move(550, 250);
      await page.mouse.up();

      // Verify all selected
      await expect(page.locator('.react-flow__node.selected')).toHaveCount(3);

      await teardownTest(page);
    });

    test('should move node with drag', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Get initial position
      const initialBox = await page.locator('.react-flow__node').boundingBox();

      // Drag node to new position
      await page
        .locator('.react-flow__node')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 500, y: 300 },
        });

      // Verify position changed
      const finalBox = await page.locator('.react-flow__node').boundingBox();
      expect(finalBox?.x).not.toBe(initialBox?.x);
      expect(finalBox?.y).not.toBe(initialBox?.y);

      // Verify undo available
      await page.keyboard.press('Meta+z');
      const undoneBox = await page.locator('.react-flow__node').boundingBox();
      expect(undoneBox?.x).toBeCloseTo(initialBox!.x, 1);

      await teardownTest(page);
    });

    test('should move multiple nodes together', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add three nodes
      for (let i = 0; i < 3; i++) {
        await page
          .locator('[data-testid="palette-item-api"]')
          .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
            targetPosition: { x: 200 + i * 100, y: 200 },
          });
      }

      // Select all
      await page.keyboard.press('Meta+a');

      // Drag first node
      await page
        .locator('.react-flow__node')
        .first()
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 250, y: 300 },
        });

      // Verify all nodes moved (relative positions maintained)
      const nodes = page.locator('.react-flow__node');
      for (let i = 0; i < 3; i++) {
        const box = await nodes.nth(i).boundingBox();
        expect(box?.y).toBeGreaterThan(250); // Moved down
      }

      await teardownTest(page);
    });

    test('should delete node with Delete key', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Select and delete
      await page.locator('.react-flow__node').click();
      await page.keyboard.press('Delete');

      // Verify removed
      await expect(page.locator('.react-flow__node')).toHaveCount(0);

      await teardownTest(page);
    });

    test('should copy and paste nodes', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Select, copy, paste
      await page.locator('.react-flow__node').click();
      await page.keyboard.press('Meta+c');
      await page.keyboard.press('Meta+v');

      // Verify duplicate created
      await expect(page.locator('.react-flow__node')).toHaveCount(2);

      await teardownTest(page);
    });

    test('should duplicate nodes with Cmd+D', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add node
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 200 },
        });

      // Select and duplicate
      await page.locator('.react-flow__node').click();
      await page.keyboard.press('Meta+d');

      // Verify duplicate at offset
      await expect(page.locator('.react-flow__node')).toHaveCount(2);

      await teardownTest(page);
    });
  });

  test.describe('Edge Operations', () => {
    test('should create edge by dragging handles', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add two nodes
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 200, y: 200 },
        });
      await page
        .locator('[data-testid="palette-item-data"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 400, y: 200 },
        });

      // Connect by dragging from source to target handle
      const sourceHandle = page.locator('.react-flow__handle-right').first();
      const targetHandle = page.locator('.react-flow__handle-left').last();

      await sourceHandle.dragTo(targetHandle);

      // Verify connection created
      await expect(page.locator('.react-flow__edge')).toHaveCount(1);

      await teardownTest(page);
    });

    test('should delete edge with Delete key', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add two nodes and connect
      await page
        .locator('[data-testid="palette-item-api"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 200, y: 200 },
        });
      await page
        .locator('[data-testid="palette-item-data"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 400, y: 200 },
        });

      const sourceHandle = page.locator('.react-flow__handle-right').first();
      const targetHandle = page.locator('.react-flow__handle-left').last();
      await sourceHandle.dragTo(targetHandle);

      // Click edge and delete
      await page.locator('.react-flow__edge').click();
      await page.keyboard.press('Delete');

      // Verify removed
      await expect(page.locator('.react-flow__edge')).toHaveCount(0);

      await teardownTest(page);
    });
  });

  test.describe('Grouping', () => {
    test('should group selected nodes', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add three nodes
      for (let i = 0; i < 3; i++) {
        await page
          .locator('[data-testid="palette-item-api"]')
          .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
            targetPosition: { x: 200 + i * 100, y: 200 },
          });
      }

      // Select all and group
      await page.keyboard.press('Meta+a');
      await page.keyboard.press('Meta+g');

      // Verify group created
      await expect(page.locator('[data-node-type="group"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should ungroup nodes', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add and group nodes
      for (let i = 0; i < 3; i++) {
        await page
          .locator('[data-testid="palette-item-api"]')
          .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
            targetPosition: { x: 200 + i * 100, y: 200 },
          });
      }
      await page.keyboard.press('Meta+a');
      await page.keyboard.press('Meta+g');

      // Ungroup
      await page.locator('[data-node-type="group"]').click();
      await page.keyboard.press('Meta+Shift+g');

      // Verify nodes ungrouped
      await expect(page.locator('[data-node-type="group"]')).not.toBeVisible();
      await expect(page.locator('.react-flow__node')).toHaveCount(3);

      await teardownTest(page);
    });

    test('should move grouped nodes together', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add and group nodes
      for (let i = 0; i < 3; i++) {
        await page
          .locator('[data-testid="palette-item-api"]')
          .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
            targetPosition: { x: 200 + i * 100, y: 200 },
          });
      }
      await page.keyboard.press('Meta+a');
      await page.keyboard.press('Meta+g');

      // Drag group
      await page
        .locator('[data-node-type="group"]')
        .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 400 },
        });

      // Verify all nodes moved
      const nodes = page.locator('.react-flow__node');
      for (let i = 0; i < 3; i++) {
        const box = await nodes.nth(i).boundingBox();
        expect(box?.y).toBeGreaterThan(350); // Moved down
      }

      await teardownTest(page);
    });
  });

  // ============================================
  // SECTION 4: TOOLS (20 tests)
  // ============================================

  test.describe('Canvas Tools', () => {
    test.describe('Drawing Tools', () => {
      test('should activate pen tool with P key', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Press P
        await page.keyboard.press('p');

        // Verify pen tool active
        await expect(
          page.locator('[data-testid="sketch-tool-pen"].active')
        ).toBeVisible();

        await teardownTest(page);
      });

      test('should draw freehand stroke', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Activate pen tool
        await page.keyboard.press('p');

        // Draw stroke
        const canvas = page.locator('[data-testid="sketch-layer"]');
        await canvas.hover({ position: { x: 100, y: 100 } });
        await page.mouse.down();
        await page.mouse.move(200, 150);
        await page.mouse.move(300, 100);
        await page.mouse.up();

        // Verify stroke rendered
        await expect(page.locator('[data-testid="sketch-stroke"]')).toHaveCount(
          1
        );

        await teardownTest(page);
      });

      test('should erase stroke with eraser tool', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Draw stroke
        await page.keyboard.press('p');
        const canvas = page.locator('[data-testid="sketch-layer"]');
        await canvas.hover({ position: { x: 100, y: 100 } });
        await page.mouse.down();
        await page.mouse.move(200, 150);
        await page.mouse.up();

        // Activate eraser
        await page.locator('[data-testid="sketch-tool-eraser"]').click();

        // Drag over stroke
        await canvas.hover({ position: { x: 150, y: 125 } });
        await page.mouse.down();
        await page.mouse.move(180, 140);
        await page.mouse.up();

        // Verify stroke removed
        await expect(page.locator('[data-testid="sketch-stroke"]')).toHaveCount(
          0
        );

        await teardownTest(page);
      });

      test('should change stroke color', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Select color
        await page.locator('[data-testid="color-picker"]').click();
        await page.locator('[data-color="#ff0000"]').click();

        // Activate pen and draw
        await page.keyboard.press('p');
        const canvas = page.locator('[data-testid="sketch-layer"]');
        await canvas.hover({ position: { x: 100, y: 100 } });
        await page.mouse.down();
        await page.mouse.move(200, 150);
        await page.mouse.up();

        // Verify correct color
        const stroke = page.locator('[data-testid="sketch-stroke"]');
        await expect(stroke).toHaveAttribute('stroke', '#ff0000');

        await teardownTest(page);
      });
    });

    test.describe('Shape Tools', () => {
      test('should create rectangle with R key', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Press R
        await page.keyboard.press('r');

        // Drag to create rectangle
        const canvas = page.locator('[data-testid="sketch-layer"]');
        await canvas.hover({ position: { x: 100, y: 100 } });
        await page.mouse.down();
        await page.mouse.move(200, 200);
        await page.mouse.up();

        // Verify shape created
        await expect(
          page.locator('[data-shape-type="rectangle"]')
        ).toBeVisible();

        await teardownTest(page);
      });

      test('should create ellipse with E key', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Press E
        await page.keyboard.press('e');

        // Drag to create ellipse
        const canvas = page.locator('[data-testid="sketch-layer"]');
        await canvas.hover({ position: { x: 100, y: 100 } });
        await page.mouse.down();
        await page.mouse.move(200, 200);
        await page.mouse.up();

        // Verify shape created
        await expect(page.locator('[data-shape-type="ellipse"]')).toBeVisible();

        await teardownTest(page);
      });
    });

    test.describe('Text Tool', () => {
      test('should create text node with T key', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Press T
        await page.keyboard.press('t');

        // Click canvas
        const canvas = page.locator('[data-testid="react-flow-wrapper"]');
        await canvas.click({ position: { x: 300, y: 200 } });

        // Type text
        await page.keyboard.type('Test Text');
        await page.keyboard.press('Escape');

        // Verify text node created
        await expect(page.locator('[data-node-type="text"]')).toHaveText(
          /Test Text/
        );

        await teardownTest(page);
      });

      test('should edit text by double-clicking', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Create text node
        await page.keyboard.press('t');
        await page
          .locator('[data-testid="react-flow-wrapper"]')
          .click({ position: { x: 300, y: 200 } });
        await page.keyboard.type('Original');
        await page.keyboard.press('Escape');

        // Double-click to edit
        await page.locator('[data-node-type="text"]').dblclick();

        // Edit text
        await page.keyboard.press('Meta+a');
        await page.keyboard.type('Edited');
        await page.keyboard.press('Escape');

        // Verify changes saved
        await expect(page.locator('[data-node-type="text"]')).toHaveText(
          /Edited/
        );

        await teardownTest(page);
      });
    });

    // ============================================
    // SECTION 5: ALIGNMENT & DISTRIBUTION (10 tests)
    // ============================================

    test.describe('Alignment & Distribution', () => {
      test('should align nodes left', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Add 3 nodes at different x positions
        await addNode(page, 'api', { x: 100, y: 200 });
        await addNode(page, 'data', { x: 300, y: 250 });
        await addNode(page, 'component', { x: 500, y: 300 });

        // Select all nodes
        await page.keyboard.press('Control+a');
        await expect(page.locator('.react-flow__node.selected')).toHaveCount(3);

        // Align left
        await page.locator('[data-testid="toolbar-align-left"]').click();

        // Verify all nodes have same x position (leftmost = 100)
        const nodes = await page.locator('.react-flow__node').all();
        const positions = await Promise.all(nodes.map((n) => n.boundingBox()));
        const xPositions = positions.map((p) => p?.x);

        expect(xPositions[0]).toBe(xPositions[1]);
        expect(xPositions[1]).toBe(xPositions[2]);

        await teardownTest(page);
      });

      test('should align nodes center horizontally', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 100, y: 200 });
        await addNode(page, 'data', { x: 500, y: 250 });

        await page.keyboard.press('Control+a');
        await page.locator('[data-testid="toolbar-align-center-h"]').click();

        // Verify nodes centered horizontally
        const nodes = await page.locator('.react-flow__node').all();
        const boxes = await Promise.all(nodes.map((n) => n.boundingBox()));
        const centers = boxes.map((b) => b && b.x + b.width / 2);

        expect(Math.abs(centers[0]! - centers[1]!)).toBeLessThan(5);

        await teardownTest(page);
      });

      test('should align nodes right', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 100, y: 200 });
        await addNode(page, 'data', { x: 300, y: 250 });
        await addNode(page, 'component', { x: 500, y: 300 });

        await page.keyboard.press('Control+a');
        await page.locator('[data-testid="toolbar-align-right"]').click();

        // Verify all nodes aligned to rightmost edge
        const nodes = await page.locator('.react-flow__node').all();
        const boxes = await Promise.all(nodes.map((n) => n.boundingBox()));
        const rightEdges = boxes.map((b) => b && b.x + b.width);

        expect(rightEdges[0]).toBe(rightEdges[1]);
        expect(rightEdges[1]).toBe(rightEdges[2]);

        await teardownTest(page);
      });

      test('should align nodes top', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 200, y: 100 });
        await addNode(page, 'data', { x: 300, y: 300 });
        await addNode(page, 'component', { x: 400, y: 500 });

        await page.keyboard.press('Control+a');
        await page.locator('[data-testid="toolbar-align-top"]').click();

        const nodes = await page.locator('.react-flow__node').all();
        const positions = await Promise.all(nodes.map((n) => n.boundingBox()));
        const yPositions = positions.map((p) => p?.y);

        expect(yPositions[0]).toBe(yPositions[1]);
        expect(yPositions[1]).toBe(yPositions[2]);

        await teardownTest(page);
      });

      test('should align nodes middle vertically', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 200, y: 100 });
        await addNode(page, 'data', { x: 300, y: 500 });

        await page.keyboard.press('Control+a');
        await page.locator('[data-testid="toolbar-align-center-v"]').click();

        const nodes = await page.locator('.react-flow__node').all();
        const boxes = await Promise.all(nodes.map((n) => n.boundingBox()));
        const centers = boxes.map((b) => b && b.y + b.height / 2);

        expect(Math.abs(centers[0]! - centers[1]!)).toBeLessThan(5);

        await teardownTest(page);
      });

      test('should align nodes bottom', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 200, y: 100 });
        await addNode(page, 'data', { x: 300, y: 300 });
        await addNode(page, 'component', { x: 400, y: 500 });

        await page.keyboard.press('Control+a');
        await page.locator('[data-testid="toolbar-align-bottom"]').click();

        const nodes = await page.locator('.react-flow__node').all();
        const boxes = await Promise.all(nodes.map((n) => n.boundingBox()));
        const bottomEdges = boxes.map((b) => b && b.y + b.height);

        expect(bottomEdges[0]).toBe(bottomEdges[1]);
        expect(bottomEdges[1]).toBe(bottomEdges[2]);

        await teardownTest(page);
      });

      test('should distribute nodes horizontally', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Add 3 nodes with uneven spacing
        await addNode(page, 'api', { x: 100, y: 200 });
        await addNode(page, 'data', { x: 250, y: 200 });
        await addNode(page, 'component', { x: 600, y: 200 });

        await page.keyboard.press('Control+a');
        await page.locator('[data-testid="toolbar-distribute-h"]').click();

        // Verify even spacing
        const nodes = await page.locator('.react-flow__node').all();
        const boxes = await Promise.all(nodes.map((n) => n.boundingBox()));
        const xPositions = boxes.map((b) => b!.x).sort((a, b) => a - b);

        const gap1 = xPositions[1] - xPositions[0];
        const gap2 = xPositions[2] - xPositions[1];

        expect(Math.abs(gap1 - gap2)).toBeLessThan(10);

        await teardownTest(page);
      });

      test('should distribute nodes vertically', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 300, y: 100 });
        await addNode(page, 'data', { x: 300, y: 200 });
        await addNode(page, 'component', { x: 300, y: 500 });

        await page.keyboard.press('Control+a');
        await page.locator('[data-testid="toolbar-distribute-v"]').click();

        const nodes = await page.locator('.react-flow__node').all();
        const boxes = await Promise.all(nodes.map((n) => n.boundingBox()));
        const yPositions = boxes.map((b) => b!.y).sort((a, b) => a - b);

        const gap1 = yPositions[1] - yPositions[0];
        const gap2 = yPositions[2] - yPositions[1];

        expect(Math.abs(gap1 - gap2)).toBeLessThan(10);

        await teardownTest(page);
      });

      test('should snap to grid when enabled', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Enable snap to grid
        await page.locator('[data-testid="toolbar-snap-to-grid"]').click();
        await expect(
          page.locator('[data-testid="toolbar-snap-to-grid"]')
        ).toHaveClass(/active/);

        // Add node - should snap to grid (e.g., 16px grid)
        await addNode(page, 'api', { x: 107, y: 213 });

        const node = page.locator('.react-flow__node').first();
        const box = await node.boundingBox();

        // Verify snapped to grid (multiple of 16)
        expect(box!.x % 16).toBe(0);
        expect(box!.y % 16).toBe(0);

        await teardownTest(page);
      });

      test('should show smart guides when aligning manually', async ({
        page,
      }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Add reference node
        await addNode(page, 'api', { x: 300, y: 200 });

        // Add second node
        await addNode(page, 'data', { x: 500, y: 400 });

        // Drag second node toward alignment with first
        const node2 = page.locator('.react-flow__node').nth(1);
        await node2.dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 300, y: 400 }, // Same x as first node
        });

        // Verify smart guide appears (vertical line)
        await expect(
          page.locator('[data-testid="smart-guide-vertical"]')
        ).toBeVisible();

        await teardownTest(page);
      });
    });

    // ============================================
    // SECTION 6: LAYERS & Z-INDEX (8 tests)
    // ============================================

    test.describe('Layers & Z-Index', () => {
      test('should bring node to front', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Add 3 overlapping nodes
        await addNode(page, 'api', { x: 300, y: 200 });
        await addNode(page, 'data', { x: 320, y: 220 });
        await addNode(page, 'component', { x: 340, y: 240 });

        // Select first node (should be at back)
        await page.locator('.react-flow__node').first().click();

        // Bring to front
        await page.keyboard.press('Control+Shift+]');

        // Verify z-index changed (first node now has highest z-index)
        const firstNode = page.locator('.react-flow__node').first();
        const zIndex = await firstNode.evaluate(
          (el) => window.getComputedStyle(el).zIndex
        );

        expect(parseInt(zIndex)).toBeGreaterThan(2);

        await teardownTest(page);
      });

      test('should send node to back', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 300, y: 200 });
        await addNode(page, 'data', { x: 320, y: 220 });
        await addNode(page, 'component', { x: 340, y: 240 });

        // Select last node (should be at front)
        await page.locator('.react-flow__node').last().click();

        // Send to back
        await page.keyboard.press('Control+Shift+[');

        const lastNode = page.locator('.react-flow__node').last();
        const zIndex = await lastNode.evaluate(
          (el) => window.getComputedStyle(el).zIndex
        );

        expect(parseInt(zIndex)).toBe(0);

        await teardownTest(page);
      });

      test('should bring node forward one layer', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 300, y: 200 });
        await addNode(page, 'data', { x: 320, y: 220 });
        await addNode(page, 'component', { x: 340, y: 240 });

        const middleNode = page.locator('.react-flow__node').nth(1);
        await middleNode.click();

        const zIndexBefore = await middleNode.evaluate(
          (el) => window.getComputedStyle(el).zIndex
        );

        // Bring forward
        await page.keyboard.press('Control+]');

        const zIndexAfter = await middleNode.evaluate(
          (el) => window.getComputedStyle(el).zIndex
        );

        expect(parseInt(zIndexAfter)).toBe(parseInt(zIndexBefore) + 1);

        await teardownTest(page);
      });

      test('should send node backward one layer', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await addNode(page, 'api', { x: 300, y: 200 });
        await addNode(page, 'data', { x: 320, y: 220 });
        await addNode(page, 'component', { x: 340, y: 240 });

        const middleNode = page.locator('.react-flow__node').nth(1);
        await middleNode.click();

        const zIndexBefore = await middleNode.evaluate(
          (el) => window.getComputedStyle(el).zIndex
        );

        // Send backward
        await page.keyboard.press('Control+[');

        const zIndexAfter = await middleNode.evaluate(
          (el) => window.getComputedStyle(el).zIndex
        );

        expect(parseInt(zIndexAfter)).toBe(parseInt(zIndexBefore) - 1);

        await teardownTest(page);
      });

      test('should show layer panel', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        // Open layer panel
        await page.locator('[data-testid="toolbar-layers"]').click();

        await expect(page.locator('[data-testid="layer-panel"]')).toBeVisible();

        // Add nodes and verify they appear in layer panel
        await addNode(page, 'api', { x: 300, y: 200 });
        await addNode(page, 'data', { x: 400, y: 300 });

        await expect(
          page.locator('[data-testid="layer-panel-item"]')
        ).toHaveCount(2);

        await teardownTest(page);
      });

      test('should reorder layers in layer panel', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await page.locator('[data-testid="toolbar-layers"]').click();

        await addNode(page, 'api', { x: 300, y: 200 });
        await addNode(page, 'data', { x: 400, y: 300 });

        // Drag first layer to bottom
        const firstLayer = page
          .locator('[data-testid="layer-panel-item"]')
          .first();
        const lastLayer = page
          .locator('[data-testid="layer-panel-item"]')
          .last();

        await firstLayer.dragTo(lastLayer, { targetPosition: { x: 0, y: 50 } });

        // Verify order changed
        const firstLayerText = await page
          .locator('[data-testid="layer-panel-item"]')
          .first()
          .textContent();
        expect(firstLayerText).toContain('data');

        await teardownTest(page);
      });

      test('should lock layer to prevent editing', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await page.locator('[data-testid="toolbar-layers"]').click();
        await addNode(page, 'api', { x: 300, y: 200 });

        // Lock layer
        await page
          .locator('[data-testid="layer-panel-item"]')
          .first()
          .locator('[data-testid="layer-lock-toggle"]')
          .click();

        await expect(
          page.locator('[data-testid="layer-lock-icon"]')
        ).toBeVisible();

        // Try to move node - should fail
        const node = page.locator('.react-flow__node').first();
        const initialBox = await node.boundingBox();

        await node.dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
          targetPosition: { x: 500, y: 400 },
        });

        const finalBox = await node.boundingBox();

        // Position should not change
        expect(initialBox?.x).toBe(finalBox?.x);
        expect(initialBox?.y).toBe(finalBox?.y);

        await teardownTest(page);
      });

      test('should hide layer', async ({ page }) => {
        await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

        await page.locator('[data-testid="toolbar-layers"]').click();
        await addNode(page, 'api', { x: 300, y: 200 });

        // Hide layer
        await page
          .locator('[data-testid="layer-panel-item"]')
          .first()
          .locator('[data-testid="layer-visibility-toggle"]')
          .click();

        // Node should be hidden
        await expect(page.locator('.react-flow__node').first()).toBeHidden();

        // Show layer again
        await page
          .locator('[data-testid="layer-panel-item"]')
          .first()
          .locator('[data-testid="layer-visibility-toggle"]')
          .click();

        await expect(page.locator('.react-flow__node').first()).toBeVisible();

        await teardownTest(page);
      });
    });
  });

  // Helper functions
  function addNode(
    page: Page,
    type: string,
    position: { x: number; y: number }
  ) {
    return page
      .locator(`[data-testid="palette-item-${type}"]`)
      .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
        targetPosition: position,
      });
  }
});
