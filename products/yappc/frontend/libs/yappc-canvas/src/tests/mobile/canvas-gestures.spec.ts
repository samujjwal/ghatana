/**
 * Mobile Canvas - Touch Gesture Tests
 * 
 * Tests touch interactions on mobile devices including:
 * - Single tap (node selection)
 * - Double tap (node editing)
 * - Pinch zoom
 * - Pan/drag
 * - Swipe gestures
 */

import { test, expect } from '@playwright/test';

test.describe('Mobile Canvas - Touch Gestures', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/canvas/test-canvas');
    await page.waitForSelector('[data-testid="canvas-container"]');
  });

  test('should select node on single tap @mobile @visual', async ({ page }) => {
    const node = page.locator('[data-testid="node-1"]');
    await node.tap();
    
    // Verify node is selected
    await expect(node).toHaveClass(/selected/);
    
    // Verify selection toolbar appears
    await expect(page.locator('[data-testid="selection-toolbar"]')).toBeVisible();
  });

  test('should deselect node on background tap @mobile', async ({ page }) => {
    // Select a node first
    await page.locator('[data-testid="node-1"]').tap();
    await expect(page.locator('[data-testid="node-1"]')).toHaveClass(/selected/);
    
    // Tap on background
    const canvas = page.locator('[data-testid="canvas-background"]');
    await canvas.tap({ position: { x: 100, y: 100 } });
    
    // Verify node is deselected
    await expect(page.locator('[data-testid="node-1"]')).not.toHaveClass(/selected/);
  });

  test('should open node editor on double tap @mobile', async ({ page }) => {
    const node = page.locator('[data-testid="node-1"]');
    
    // Double tap on node
    await node.dblclick();
    
    // Verify editor modal appears
    await expect(page.locator('[data-testid="node-editor-modal"]')).toBeVisible();
    
    // Verify input is focused (mobile keyboard should appear)
    const isMobile = page.viewportSize()!.width < 768;
    if (isMobile) {
      await expect(page.locator('input[data-testid="node-label"]')).toBeFocused();
    }
  });

  test('should zoom canvas on pinch gesture @mobile', async ({ page }) => {
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Get initial zoom level
    const initialZoom = await canvas.evaluate((el) => {
      const transform = window.getComputedStyle(el).transform;
      const matrix = new DOMMatrix(transform);
      return matrix.a; // scale factor
    });
    
    // Simulate pinch zoom (zoom in)
    await canvas.evaluate((el) => {
      const centerX = el.clientWidth / 2;
      const centerY = el.clientHeight / 2;
      
      // Start pinch
      const touchStart = new TouchEvent('touchstart', {
        touches: [
          new Touch({ identifier: 0, target: el, clientX: centerX - 50, clientY: centerY }),
          new Touch({ identifier: 1, target: el, clientX: centerX + 50, clientY: centerY }),
        ],
      });
      el.dispatchEvent(touchStart);
      
      // Move fingers apart (zoom in)
      const touchMove = new TouchEvent('touchmove', {
        touches: [
          new Touch({ identifier: 0, target: el, clientX: centerX - 100, clientY: centerY }),
          new Touch({ identifier: 1, target: el, clientX: centerX + 100, clientY: centerY }),
        ],
      });
      el.dispatchEvent(touchMove);
      
      // End gesture
      el.dispatchEvent(new TouchEvent('touchend'));
    });
    
    // Wait for animation
    await page.waitForTimeout(300);
    
    // Verify zoom increased
    const finalZoom = await canvas.evaluate((el) => {
      const transform = window.getComputedStyle(el).transform;
      const matrix = new DOMMatrix(transform);
      return matrix.a;
    });
    
    expect(finalZoom).toBeGreaterThan(initialZoom);
  });

  test('should pan canvas on drag gesture @mobile', async ({ page }) => {
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Get initial position
    const initialTransform = await canvas.evaluate((el) => {
      const transform = window.getComputedStyle(el).transform;
      const matrix = new DOMMatrix(transform);
      return { x: matrix.e, y: matrix.f };
    });
    
    // Perform drag gesture
    await canvas.evaluate((el) => {
      const startX = 300;
      const startY = 300;
      const endX = 100;
      const endY = 100;
      
      // Touch start
      const touchStart = new TouchEvent('touchstart', {
        touches: [new Touch({ identifier: 0, target: el, clientX: startX, clientY: startY })],
      });
      el.dispatchEvent(touchStart);
      
      // Touch move
      const touchMove = new TouchEvent('touchmove', {
        touches: [new Touch({ identifier: 0, target: el, clientX: endX, clientY: endY })],
      });
      el.dispatchEvent(touchMove);
      
      // Touch end
      el.dispatchEvent(new TouchEvent('touchend'));
    });
    
    // Wait for animation
    await page.waitForTimeout(300);
    
    // Verify canvas moved
    const finalTransform = await canvas.evaluate((el) => {
      const transform = window.getComputedStyle(el).transform;
      const matrix = new DOMMatrix(transform);
      return { x: matrix.e, y: matrix.f };
    });
    
    expect(finalTransform.x).not.toBe(initialTransform.x);
    expect(finalTransform.y).not.toBe(initialTransform.y);
  });

  test('should drag node to new position @mobile', async ({ page }) => {
    const node = page.locator('[data-testid="node-1"]');
    
    // Get initial position
    const initialBox = await node.boundingBox();
    expect(initialBox).not.toBeNull();
    
    // Long press to start drag
    await node.tap({ delay: 500 });
    
    // Drag to new position
    await page.touchscreen.tap(initialBox!.x + 100, initialBox!.y + 100);
    
    // Wait for position update
    await page.waitForTimeout(300);
    
    // Verify node moved
    const finalBox = await node.boundingBox();
    expect(finalBox).not.toBeNull();
    expect(finalBox!.x).toBeGreaterThan(initialBox!.x);
    expect(finalBox!.y).toBeGreaterThan(initialBox!.y);
  });

  test('should respond to touch within 100ms @mobile @performance', async ({ page }) => {
    const node = page.locator('[data-testid="node-1"]');
    
    // Measure touch response time
    const startTime = Date.now();
    await node.tap();
    
    // Wait for visual feedback
    await expect(node).toHaveClass(/selected/);
    const endTime = Date.now();
    
    const responseTime = endTime - startTime;
    expect(responseTime).toBeLessThan(100);
  });

  test('should handle simultaneous touches gracefully @mobile', async ({ page }) => {
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Simulate two fingers touching different areas
    await canvas.evaluate((el) => {
      const event = new TouchEvent('touchstart', {
        touches: [
          new Touch({ identifier: 0, target: el, clientX: 100, clientY: 100 }),
          new Touch({ identifier: 1, target: el, clientX: 300, clientY: 300 }),
        ],
      });
      el.dispatchEvent(event);
    });
    
    // Should not crash or behave unexpectedly
    await expect(canvas).toBeVisible();
  });

  test('should handle touch during zoom animation @mobile', async ({ page }) => {
    // Start zoom animation
    await page.locator('[data-testid="zoom-in-button"]').tap();
    
    // Immediately try to interact
    await page.locator('[data-testid="node-1"]').tap();
    
    // Should complete both actions without errors
    await expect(page.locator('[data-testid="node-1"]')).toHaveClass(/selected/);
  });
});

test.describe('Mobile Canvas - Responsive Layout', () => {
  const viewports = [
    { name: 'iPhone SE', width: 375, height: 667 },
    { name: 'iPhone 13', width: 390, height: 844 },
    { name: 'iPad', width: 768, height: 1024 },
    { name: 'iPad Pro', width: 1024, height: 1366 },
  ];

  for (const viewport of viewports) {
    test(`should render correctly on ${viewport.name} @mobile @visual`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await page.goto('/canvas/test-canvas');
      
      // Verify canvas fills viewport
      const canvas = page.locator('[data-testid="canvas-container"]');
      const box = await canvas.boundingBox();
      expect(box).not.toBeNull();
      
      expect(box!.width).toBeGreaterThan(viewport.width * 0.9);
      expect(box!.height).toBeGreaterThan(viewport.height * 0.7);
      
      // Verify toolbar is visible
      await expect(page.locator('[data-testid="mobile-toolbar"]')).toBeVisible();
      
      // Take screenshot for visual regression
      await page.screenshot({ 
        path: `test-results/screenshots/canvas-${viewport.name.toLowerCase().replace(' ', '-')}.png`,
        fullPage: true 
      });
    });
  }

  test('should handle orientation change @mobile', async ({ page }) => {
    await page.goto('/canvas/test-canvas');
    
    // Portrait mode
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.locator('[data-testid="canvas-container"]')).toBeVisible();
    
    // Switch to landscape
    await page.setViewportSize({ width: 844, height: 390 });
    
    // Verify canvas adapts
    await expect(page.locator('[data-testid="canvas-container"]')).toBeVisible();
    
    // Verify toolbar repositions
    const toolbar = page.locator('[data-testid="mobile-toolbar"]');
    const toolbarBox = await toolbar.boundingBox();
    expect(toolbarBox).not.toBeNull();
    expect(toolbarBox!.y).toBeLessThan(100); // Should be at top in landscape
  });
});

test.describe('Mobile Canvas - Accessibility', () => {
  test('should have touch targets at least 44x44px @mobile @a11y', async ({ page }) => {
    await page.goto('/canvas/test-canvas');
    
    const buttons = page.locator('button');
    const count = await buttons.count();
    
    for (let i = 0; i < count; i++) {
      const button = buttons.nth(i);
      const box = await button.boundingBox();
      
      if (box) {
        expect(box.width).toBeGreaterThanOrEqual(44);
        expect(box.height).toBeGreaterThanOrEqual(44);
      }
    }
  });

  test('should announce touch interactions to screen readers @mobile @a11y', async ({ page }) => {
    await page.goto('/canvas/test-canvas');
    
    const node = page.locator('[data-testid="node-1"]');
    
    // Verify ARIA labels
    await expect(node).toHaveAttribute('role', 'button');
    await expect(node).toHaveAttribute('aria-label');
    
    // Tap node
    await node.tap();
    
    // Verify live region announces selection
    const liveRegion = page.locator('[aria-live="polite"]');
    await expect(liveRegion).toContainText('selected');
  });
});
