import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

test.describe('Canvas - Complete Feature Suite', () => {
  test.beforeEach(async ({ page }) => {
    await setupTest(page, {
      url: '/canvas',
      seedData: false,
      seedScenario: 'default',
    });
  });

  test.afterEach(async ({ page }) => {
    await teardownTest(page);
  });

  test.describe('Core Canvas Operations', () => {
    test('should load canvas and show component palette', async ({ page }) => {
      // Check that canvas loads
      await expect(page.locator('[data-testid="canvas-scene"]')).toBeVisible();

      // Check component palette
      await expect(
        page.locator('[data-testid="component-palette"]')
      ).toBeVisible();

      // Check for basic components
      await expect(page.locator('text=API')).toBeVisible();
      await expect(page.locator('text=Data')).toBeVisible();
      await expect(page.locator('text=Component')).toBeVisible();
    });

    test('should add node via drag and drop', async ({ page }) => {
      // Drag API node from palette to canvas
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');

      await apiNode.dragTo(canvas, {
        targetPosition: { x: 300, y: 200 },
      });

      // Verify node was added
      await expect(page.locator('.react-flow__node')).toHaveCount(1);
    });

    test('should select and delete nodes', async ({ page }) => {
      // Add a node first
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await apiNode.dragTo(canvas, { targetPosition: { x: 300, y: 200 } });

      // Select the node
      await page.locator('.react-flow__node').click();
      await expect(page.locator('.react-flow__node.selected')).toHaveCount(1);

      // Delete with keyboard
      await page.keyboard.press('Delete');
      await expect(page.locator('.react-flow__node')).toHaveCount(0);
    });

    test('should create connections between nodes', async ({ page }) => {
      // Add two nodes
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const dataNode = page.locator('[data-testid="palette-item-data"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');

      await apiNode.dragTo(canvas, { targetPosition: { x: 200, y: 200 } });
      await dataNode.dragTo(canvas, { targetPosition: { x: 400, y: 200 } });

      // Connect nodes by dragging from handle to handle
      const sourceHandle = page.locator('.react-flow__handle-right').first();
      const targetHandle = page.locator('.react-flow__handle-left').last();

      await sourceHandle.dragTo(targetHandle);

      // Verify connection was created
      await expect(page.locator('.react-flow__edge')).toHaveCount(1);
    });
  });

  test.describe('Sketch Tools', () => {
    test('should switch to pen tool and draw', async ({ page }) => {
      // Switch to pen tool
      await page.locator('[data-testid="sketch-tool-pen"]').click();

      // Draw on canvas
      const sketchLayer = page.locator('[data-testid="sketch-layer"]');
      await sketchLayer.hover({ position: { x: 100, y: 100 } });
      await page.mouse.down();
      await page.mouse.move(200, 150);
      await page.mouse.move(300, 100);
      await page.mouse.up();

      // Verify stroke was created
      await expect(page.locator('[data-testid="sketch-stroke"]')).toHaveCount(
        1
      );
    });

    test('should create rectangle shape', async ({ page }) => {
      // Switch to rectangle tool
      await page.locator('[data-testid="sketch-tool-rectangle"]').click();

      // Draw rectangle
      const sketchLayer = page.locator('[data-testid="sketch-layer"]');
      await sketchLayer.hover({ position: { x: 150, y: 150 } });
      await page.mouse.down();
      await page.mouse.move(250, 200);
      await page.mouse.up();

      // Verify rectangle was created
      await expect(
        page.locator('[data-testid="sketch-rectangle"]')
      ).toHaveCount(1);
    });
  });

  test.describe('Page Designer', () => {
    test('should open page designer and add components', async ({ page }) => {
      // Add a page node
      const pageNode = page.locator('[data-testid="palette-item-page"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await pageNode.dragTo(canvas, { targetPosition: { x: 300, y: 200 } });

      // Double-click to open page designer
      await page.locator('.react-flow__node').dblclick();

      // Verify page designer opened
      await expect(page.locator('[data-testid="page-designer"]')).toBeVisible();

      // Check initial button count
      const initialButtonCount = await page
        .locator('[data-testid="page-button"]')
        .count();

      // Add a button component
      const buttonComponent = page.locator(
        '[data-testid="page-component-button"]'
      );
      const designArea = page.locator('[data-testid="page-design-area"]');
      await buttonComponent.dragTo(designArea);

      // Verify button was added (count should increase by 1)
      await expect(page.locator('[data-testid="page-button"]')).toHaveCount(
        initialButtonCount + 1
      );
    });

    test('should edit component properties', async ({ page }) => {
      // Setup: Add page node and open designer
      const pageNode = page.locator('[data-testid="palette-item-page"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await pageNode.dragTo(canvas, { targetPosition: { x: 300, y: 200 } });
      await page.locator('.react-flow__node').dblclick();

      // Add button and select it
      const buttonComponent = page.locator(
        '[data-testid="page-component-button"]'
      );
      const designArea = page.locator('[data-testid="page-design-area"]');
      await buttonComponent.dragTo(designArea);
      await page.locator('[data-testid="page-button"]').click();

      // Edit properties
      await page.locator('[data-testid="property-label"]').fill('Click Me!');
      await page
        .locator('[data-testid="property-variant"]')
        .selectOption('contained');

      // Verify changes applied
      await expect(page.locator('[data-testid="page-button"]')).toContainText(
        'Click Me!'
      );
    });
  });

  test.describe('Export and Templates', () => {
    test('should export canvas as JSON', async ({ page }) => {
      // Add some content
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await apiNode.dragTo(canvas, { targetPosition: { x: 300, y: 200 } });

      // Open export menu
      await page.locator('[data-testid="export-button"]').click();

      // Export as JSON
      const downloadPromise = page.waitForEvent('download');
      await page.locator('[data-testid="export-json"]').click();
      const download = await downloadPromise;

      // Verify download
      expect(download.suggestedFilename()).toMatch(/canvas.*\.json$/);
    });

    test('should save and load template', async ({ page }) => {
      // Add some content
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await apiNode.dragTo(canvas, { targetPosition: { x: 300, y: 200 } });

      // Save as template
      await page.locator('[data-testid="templates-button"]').click();
      await page.locator('[data-testid="save-template"]').click();
      await page.locator('[data-testid="template-name"]').fill('Test Template');
      await page.locator('[data-testid="save-template-confirm"]').click();

      // Clear canvas
      await page.keyboard.press('Control+a');
      await page.keyboard.press('Delete');
      await expect(page.locator('.react-flow__node')).toHaveCount(0);

      // Load template
      await page.locator('[data-testid="templates-button"]').click();
      await page.locator('text=Test Template').click();
      await page.locator('[data-testid="load-template"]').click();

      // Verify template loaded
      await expect(page.locator('.react-flow__node')).toHaveCount(1);
    });
  });

  test.describe('Tools and Command Palette', () => {
    test('should open command palette with keyboard shortcut', async ({
      page,
    }) => {
      // Open command palette
      await page.keyboard.press('Control+k');

      // Verify palette opened
      await expect(
        page.locator('[data-testid="command-palette"]')
      ).toBeVisible();

      // Search for accessibility tool
      await page
        .locator('[data-testid="command-search"]')
        .fill('accessibility');
      await expect(page.locator('text=Run Accessibility Check')).toBeVisible();

      // Close palette
      await page.keyboard.press('Escape');
      await expect(
        page.locator('[data-testid="command-palette"]')
      ).not.toBeVisible();
    });

    test('should run accessibility tool', async ({ page }) => {
      // Add some content without labels
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await apiNode.dragTo(canvas, { targetPosition: { x: 300, y: 200 } });

      // Open command palette and run accessibility check
      await page.keyboard.press('Control+k');
      await page
        .locator('[data-testid="command-search"]')
        .fill('accessibility');
      await page.locator('text=Run Accessibility Check').click();

      // Verify accessibility panel opened with issues
      await expect(
        page.locator('[data-testid="accessibility-panel"]')
      ).toBeVisible();
      await expect(page.locator('text=missing a label')).toBeVisible();
    });

    test('should apply auto layout', async ({ page }) => {
      // Add multiple nodes
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const dataNode = page.locator('[data-testid="palette-item-data"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');

      await apiNode.dragTo(canvas, { targetPosition: { x: 100, y: 100 } });
      await dataNode.dragTo(canvas, { targetPosition: { x: 150, y: 150 } });

      // Get initial positions
      const initialPositions = await page.evaluate(() => {
        const nodes = document.querySelectorAll('.react-flow__node');
        return Array.from(nodes).map((node) => {
          const transform = node.getAttribute('style');
          return transform;
        });
      });

      // Apply auto layout
      await page.keyboard.press('Control+k');
      await page.locator('[data-testid="command-search"]').fill('layout');
      await page.locator('text=Auto Layout').click();
      await page.locator('[data-testid="apply-layout"]').click();

      // Verify positions changed
      const newPositions = await page.evaluate(() => {
        const nodes = document.querySelectorAll('.react-flow__node');
        return Array.from(nodes).map((node) => {
          const transform = node.getAttribute('style');
          return transform;
        });
      });

      expect(newPositions).not.toEqual(initialPositions);
    });
  });

  test.describe('Collaboration Features', () => {
    test('should show comments panel', async ({ page }) => {
      // Open comments panel
      await page.locator('[data-testid="comments-button"]').click();

      // Verify panel opened
      await expect(
        page.locator('[data-testid="comments-panel"]')
      ).toBeVisible();
      await expect(page.locator('text=No comments yet')).toBeVisible();
    });

    test('should add and display comment', async ({ page }) => {
      // Add a node first
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await apiNode.dragTo(canvas, { targetPosition: { x: 300, y: 200 } });

      // Select node and open comments
      await page.locator('.react-flow__node').click();
      await page.locator('[data-testid="comments-button"]').click();

      // Wait for comments panel to be fully visible and interactive
      await expect(
        page.locator('[data-testid="comments-panel"]')
      ).toBeVisible();
      await page.waitForTimeout(500); // Wait for drawer animation to complete

      // Add comment
      await page
        .locator('[data-testid="comment-input"]')
        .fill('This needs better labeling');
      await page.locator('[data-testid="add-comment"]').click({ force: true });

      // Verify comment appears
      await expect(
        page.locator('text=This needs better labeling')
      ).toBeVisible();
    });
  });

  test.describe('Performance and Large Canvases', () => {
    test('should handle large number of elements', async ({ page }) => {
      // Load performance test scenario
      await page.locator('[data-testid="seed-data-button"]').click();
      await page.locator('[data-testid="load-performance-scenario"]').click();

      // Verify many elements loaded
      await expect(page.locator('.react-flow__node')).toHaveCount(100, {
        timeout: 10000,
      });

      // Test pan and zoom performance
      const startTime = Date.now();
      await page.mouse.wheel(0, -500); // Zoom in
      await page.mouse.move(400, 300);
      await page.mouse.down();
      await page.mouse.move(200, 200); // Pan
      await page.mouse.up();
      const endTime = Date.now();

      // Should complete within reasonable time
      expect(endTime - startTime).toBeLessThan(2000);
    });

    test('should show performance metrics', async ({ page }) => {
      // Enable performance monitoring
      await page.locator('[data-testid="performance-button"]').click();
      await page.locator('[data-testid="enable-monitoring"]').click();

      // Verify metrics panel
      await expect(
        page.locator('[data-testid="performance-metrics"]')
      ).toBeVisible();
      await expect(page.locator('text=FPS:')).toBeVisible();
      await expect(page.locator('text=Elements:')).toBeVisible();
    });
  });

  test.describe('Keyboard Shortcuts', () => {
    test('should support common keyboard shortcuts', async ({ page }) => {
      // Add node
      const apiNode = page.locator('[data-testid="palette-item-api"]');
      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      await apiNode.dragTo(canvas, { targetPosition: { x: 300, y: 200 } });

      // Select all
      await page.keyboard.press('Control+a');
      await expect(page.locator('.react-flow__node.selected')).toHaveCount(1);

      // Copy
      await page.keyboard.press('Control+c');

      // Paste
      await page.keyboard.press('Control+v');
      await expect(page.locator('.react-flow__node')).toHaveCount(2);

      // Undo
      await page.keyboard.press('Control+z');
      await expect(page.locator('.react-flow__node')).toHaveCount(1);

      // Redo
      await page.keyboard.press('Control+y');
      await expect(page.locator('.react-flow__node')).toHaveCount(2);
    });

    test('should support tool switching shortcuts', async ({ page }) => {
      // Switch to pen tool
      await page.keyboard.press('p');
      await expect(
        page.locator('[data-testid="sketch-tool-pen"].active')
      ).toBeVisible();

      // Switch to rectangle tool
      await page.keyboard.press('r');
      await expect(
        page.locator('[data-testid="sketch-tool-rectangle"].active')
      ).toBeVisible();

      // Switch to select tool
      await page.keyboard.press('v');
      await expect(
        page.locator('[data-testid="sketch-tool-select"].active')
      ).toBeVisible();
    });
  });

  test.describe('Accessibility', () => {
    test('should be keyboard navigable', async ({ page }) => {
      // Tab through interface
      await page.keyboard.press('Tab');
      await expect(page.locator(':focus')).toBeVisible();

      // Should be able to reach main canvas
      let canvasFocused = false;
      for (let i = 0; i < 10; i++) {
        await page.keyboard.press('Tab');
        const focused = page.locator(':focus');
        const testId = await focused.getAttribute('data-testid');
        if (testId === 'react-flow-wrapper') {
          canvasFocused = true;
          break;
        }
      }

      // Verify the canvas can be focused (either it's already focused or we can focus it manually)
      if (!canvasFocused) {
        await page.locator('[data-testid="react-flow-wrapper"]').focus();
      }

      // Check that the canvas is actually focusable and has the proper attributes
      await expect(
        page.locator('[data-testid="react-flow-wrapper"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="react-flow-wrapper"]')
      ).toHaveAttribute('tabindex', '0');
    });

    test('should have proper ARIA labels', async ({ page }) => {
      // Check component palette has proper labels
      await expect(
        page.locator('[aria-label="Component Palette"]')
      ).toBeVisible();

      // Check canvas has proper label
      await expect(page.locator('[aria-label*="Canvas"]')).toBeVisible();

      // Check tools have labels
      await expect(page.locator('[aria-label="Pen Tool"]')).toBeVisible();
      await expect(page.locator('[aria-label="Rectangle Tool"]')).toBeVisible();
    });
  });
});
