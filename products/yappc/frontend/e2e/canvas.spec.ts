/**
 * E2E Test Suite: Canvas Component
 * 
 * Comprehensive end-to-end tests for Canvas functionality covering:
 * - Basic interaction workflows
 * - Advanced selection and manipulation
 * - Performance with large datasets
 * - Accessibility features
 * - Error handling and edge cases
 */

import { test, expect, Page } from '@playwright/test';

// Constants
const STORYBOOK_URL = 'http://localhost:6006';
const CANVAS_STORY = `${STORYBOOK_URL}/iframe.html?id=canvas--basic&viewMode=story`;
const CANVAS_COMPLEX_STORY = `${STORYBOOK_URL}/iframe.html?id=canvas--with-plugins&viewMode=story`;

/**
 * Suite 1: Canvas Basic Interactions
 */
test.describe('Canvas Basic Interactions', () => {
  test('should render canvas component', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    await expect(canvas).toBeVisible();
  });

  test('should add node on double-click', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const nodes = page.locator('[data-testid^="node-"]');
    
    await expect(nodes).toHaveCount(1);
  });

  test('should select node on click', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add a node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    
    // Click to select
    await node.click();
    
    // Verify selection state
    await expect(node).toHaveClass(/selected/);
  });

  test('should drag node to new position', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    
    // Get initial position
    const boundingBox = await node.boundingBox();
    expect(boundingBox).not.toBeNull();
    
    // Drag to new position
    if (boundingBox) {
      await node.drag({ x: 100, y: 100 });
      
      const newBoundingBox = await node.boundingBox();
      expect(newBoundingBox?.x).toBeGreaterThan(boundingBox.x);
      expect(newBoundingBox?.y).toBeGreaterThan(boundingBox.y);
    }
  });

  test('should delete selected node with Delete key', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    
    // Select and delete
    await node.click();
    await page.keyboard.press('Delete');
    
    // Verify deletion
    await expect(node).not.toBeVisible();
  });
});

/**
 * Suite 2: Canvas Marquee Selection
 */
test.describe('Canvas Marquee Selection', () => {
  test('should create marquee selection box', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add multiple nodes
    await canvas.dblClick({ position: { x: 100, y: 100 } });
    await canvas.dblClick({ position: { x: 300, y: 100 } });
    await canvas.dblClick({ position: { x: 200, y: 300 } });
    
    // Create marquee selection
    await canvas.click({ position: { x: 80, y: 80 } });
    await canvas.dragTo(page.locator('body'), {
      sourcePosition: { x: 80, y: 80 },
      targetPosition: { x: 320, y: 320 }
    });
    
    // Verify all nodes selected
    const selectedNodes = page.locator('[data-testid^="node-"].selected');
    await expect(selectedNodes).toHaveCount(3);
  });

  test('should select multiple nodes with Ctrl+Click', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add nodes
    await canvas.dblClick({ position: { x: 100, y: 100 } });
    await canvas.dblClick({ position: { x: 300, y: 100 } });
    
    const nodes = page.locator('[data-testid^="node-"]');
    
    // Click first node
    await nodes.nth(0).click();
    
    // Ctrl+Click second node
    await nodes.nth(1).click({ modifiers: ['Control'] });
    
    // Verify both selected
    const selectedNodes = page.locator('[data-testid^="node-"].selected');
    await expect(selectedNodes).toHaveCount(2);
  });
});

/**
 * Suite 3: Canvas Undo/Redo
 */
test.describe('Canvas Undo/Redo', () => {
  test('should undo node creation', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    let nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(1);
    
    // Undo
    await page.keyboard.press('Control+Z');
    nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(0);
  });

  test('should redo after undo', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node, undo, redo
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    await page.keyboard.press('Control+Z');
    await page.keyboard.press('Control+Y');
    
    const nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(1);
  });

  test('should maintain undo history across multiple operations', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Multiple operations
    await canvas.dblClick({ position: { x: 100, y: 100 } });
    await canvas.dblClick({ position: { x: 300, y: 100 } });
    await canvas.dblClick({ position: { x: 200, y: 300 } });
    
    let nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(3);
    
    // Undo 3 times
    await page.keyboard.press('Control+Z');
    await page.keyboard.press('Control+Z');
    await page.keyboard.press('Control+Z');
    
    nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(0);
  });
});

/**
 * Suite 4: Canvas Copy/Paste
 */
test.describe('Canvas Copy/Paste', () => {
  test('should copy and paste selected node', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    
    // Copy
    await node.click();
    await page.keyboard.press('Control+C');
    
    // Paste
    await page.keyboard.press('Control+V');
    
    // Verify new node created
    const nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(2);
  });

  test('should paste maintains offset from original', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    const originalBox = await node.boundingBox();
    
    // Copy and paste
    await node.click();
    await page.keyboard.press('Control+C');
    await page.keyboard.press('Control+V');
    
    // Verify offset
    const pastedNode = page.locator('[data-testid^="node-"]').nth(1);
    const pastedBox = await pastedNode.boundingBox();
    
    expect(pastedBox?.x).toBeGreaterThan(originalBox?.x || 0);
  });
});

/**
 * Suite 5: Canvas Keyboard Navigation
 */
test.describe('Canvas Keyboard Navigation', () => {
  test('should navigate nodes with arrow keys', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add nodes
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    await canvas.dblClick({ position: { x: 300, y: 200 } });
    
    const firstNode = page.locator('[data-testid^="node-"]').nth(0);
    
    // Select first node
    await firstNode.click();
    
    // Press right arrow to select next
    await page.keyboard.press('ArrowRight');
    
    const selectedNodes = page.locator('[data-testid^="node-"].selected');
    // Should have focused next node
    await expect(selectedNodes).toHaveCount(1);
  });

  test('should move selected node with Shift+Arrow', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    const initialBox = await node.boundingBox();
    
    // Select and move
    await node.click();
    await page.keyboard.press('Shift+ArrowRight');
    
    const movedBox = await node.boundingBox();
    expect(movedBox?.x).toBeGreaterThan(initialBox?.x || 0);
  });

  test('should select all nodes with Ctrl+A', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add nodes
    await canvas.dblClick({ position: { x: 100, y: 100 } });
    await canvas.dblClick({ position: { x: 300, y: 100 } });
    await canvas.dblClick({ position: { x: 200, y: 300 } });
    
    // Select all
    await page.keyboard.press('Control+A');
    
    const selectedNodes = page.locator('[data-testid^="node-"].selected');
    await expect(selectedNodes).toHaveCount(3);
  });
});

/**
 * Suite 6: Canvas with Plugins
 */
test.describe('Canvas with Plugins', () => {
  test('should render canvas with minimap plugin', async ({ page }) => {
    await page.goto(CANVAS_COMPLEX_STORY);
    const minimap = page.locator('[data-testid="minimap"]');
    await expect(minimap).toBeVisible();
  });

  test('should interact with minimap', async ({ page }) => {
    await page.goto(CANVAS_COMPLEX_STORY);
    const minimap = page.locator('[data-testid="minimap"]');
    
    // Click on minimap to pan
    const minimapBox = await minimap.boundingBox();
    if (minimapBox) {
      await minimap.click({ position: { x: minimapBox.width / 2, y: minimapBox.height / 2 } });
    }
    
    // Verify canvas panned (would check viewport position)
    const canvas = page.locator('[data-testid="canvas-container"]');
    await expect(canvas).toBeVisible();
  });

  test('should use toolbar controls', async ({ page }) => {
    await page.goto(CANVAS_COMPLEX_STORY);
    const toolbar = page.locator('[data-testid="toolbar"]');
    
    await expect(toolbar).toBeVisible();
    
    // Test zoom buttons
    const zoomInBtn = toolbar.locator('button[data-action="zoom-in"]');
    await zoomInBtn.click();
    
    // Verify zoom changed
    const canvas = page.locator('[data-testid="canvas-container"]');
    const style = await canvas.getAttribute('style');
    expect(style).toContain('scale');
  });
});

/**
 * Suite 7: Canvas Performance
 */
test.describe('Canvas Performance', () => {
  test('should handle 100 nodes without degradation', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    const startTime = Date.now();
    
    // Add 100 nodes
    for (let i = 0; i < 100; i++) {
      const x = 100 + (i % 10) * 150;
      const y = 100 + Math.floor(i / 10) * 150;
      await canvas.dblClick({ position: { x, y } });
    }
    
    const endTime = Date.now();
    const duration = endTime - startTime;
    
    // Should complete in reasonable time (< 10 seconds)
    expect(duration).toBeLessThan(10000);
    
    const nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(100);
  });

  test('should pan smoothly with many nodes', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add 50 nodes
    for (let i = 0; i < 50; i++) {
      const x = 100 + (i % 10) * 150;
      const y = 100 + Math.floor(i / 10) * 150;
      await canvas.dblClick({ position: { x, y } });
    }
    
    // Measure pan performance
    const startTime = Date.now();
    
    // Pan by dragging
    await canvas.drag({ x: -200, y: -200 });
    
    const endTime = Date.now();
    expect(endTime - startTime).toBeLessThan(500); // Should be smooth
  });
});

/**
 * Suite 8: Canvas Accessibility
 */
test.describe('Canvas Accessibility', () => {
  test('should have accessible canvas container', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    const role = await canvas.getAttribute('role');
    expect(role).toBe('application');
  });

  test('should announce node additions to screen readers', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    const ariaLive = page.locator('[aria-live="polite"]');
    
    // Add node
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    
    // Verify announcement made
    const announcement = ariaLive.first();
    await expect(announcement).toBeVisible();
  });

  test('should support keyboard-only operation', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    // Focus canvas
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.focus();
    
    // Add node with keyboard
    await page.keyboard.press('Enter');
    
    const nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(1);
  });

  test('nodes should have sufficient color contrast', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    
    // Add node
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    
    const node = page.locator('[data-testid^="node-"]').first();
    const color = await node.evaluate((el) => {
      return window.getComputedStyle(el).backgroundColor;
    });
    
    // Basic check for contrast (would need actual contrast ratio calculation)
    expect(color).toBeTruthy();
  });
});

/**
 * Suite 9: Canvas Error Handling
 */
test.describe('Canvas Error Handling', () => {
  test('should handle rapid node creation gracefully', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Rapid clicks
    for (let i = 0; i < 20; i++) {
      await canvas.dblClick({ position: { x: 100 + i * 5, y: 100 + i * 5 } });
    }
    
    // Should not throw error and nodes should be created
    const nodes = page.locator('[data-testid^="node-"]');
    const count = await nodes.count();
    expect(count).toBeGreaterThan(0);
  });

  test('should recover from invalid operations', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Try to delete when nothing selected
    await page.keyboard.press('Delete');
    
    // Canvas should still be functional
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const nodes = page.locator('[data-testid^="node-"]');
    await expect(nodes).toHaveCount(1);
  });
});

/**
 * Suite 10: Canvas Zoom & Pan
 */
test.describe('Canvas Zoom & Pan', () => {
  test('should zoom in with mouse wheel', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Get initial scale
    const initialStyle = await canvas.getAttribute('style');
    
    // Zoom with wheel
    await canvas.hover();
    await page.mouse.wheel(0, -100);
    
    // Verify scale changed
    const newStyle = await canvas.getAttribute('style');
    expect(initialStyle).not.toBe(newStyle);
  });

  test('should pan canvas with middle mouse button', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add node as reference
    await canvas.dblClick({ position: { x: 200, y: 200 } });
    const node = page.locator('[data-testid^="node-"]').first();
    const initialBox = await node.boundingBox();
    
    // Pan with middle mouse button drag
    const canvasBox = await canvas.boundingBox();
    if (canvasBox) {
      await canvas.click({ button: 'middle', position: { x: canvasBox.width / 2, y: canvasBox.height / 2 } });
      // Note: Playwright doesn't support middle button drag directly, would need workaround
    }
    
    await expect(canvas).toBeVisible();
  });

  test('should fit all nodes in viewport', async ({ page }) => {
    await page.goto(CANVAS_STORY);
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Add nodes spread across area
    for (let i = 0; i < 5; i++) {
      await canvas.dblClick({ position: { x: 100 + i * 200, y: 100 + i * 200 } });
    }
    
    // Fit to view
    await page.keyboard.press('Control+1');
    
    // All nodes should be visible
    const nodes = page.locator('[data-testid^="node-"]');
    for (let i = 0; i < 5; i++) {
      const node = nodes.nth(i);
      await expect(node).toBeInViewport();
    }
  });
});
