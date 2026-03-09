# Mobile-First Canvas Testing Guide

**Status:** Ready for Implementation  
**Priority:** High  
**Estimated Effort:** 2-3 days  
**Test Framework:** Playwright + Mobile Emulation

---

## Overview

Comprehensive testing strategy for mobile canvas interactions including:
- Touch gestures (tap, double-tap, pinch, pan, swipe)
- Responsive layout verification
- Performance on mobile devices
- Accessibility on touch devices

---

## Test Environment Setup

### Playwright Configuration

```typescript
// playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/mobile',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    // Mobile devices
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 13'] },
    },
    {
      name: 'Tablet',
      use: { ...devices['iPad Pro'] },
    },
    
    // Desktop for comparison
    {
      name: 'Desktop Chrome',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
```

---

## Touch Gesture Tests

### 1. Single Tap - Node Selection

```typescript
import { test, expect } from '@playwright/test';

test.describe('Mobile Canvas - Touch Gestures', () => {
  test('should select node on single tap @mobile @visual', async ({ page }) => {
    await page.goto('/canvas/test-canvas');
    
    // Wait for canvas to load
    await page.waitForSelector('[data-testid="canvas-container"]');
    
    // Tap on a node
    const node = page.locator('[data-testid="node-1"]');
    await node.tap();
    
    // Verify node is selected
    await expect(node).toHaveClass(/selected/);
    
    // Verify selection toolbar appears
    await expect(page.locator('[data-testid="selection-toolbar"]')).toBeVisible();
  });

  test('should deselect node on background tap', async ({ page }) => {
    await page.goto('/canvas/test-canvas');
    
    // Select a node
    await page.locator('[data-testid="node-1"]').tap();
    await expect(page.locator('[data-testid="node-1"]')).toHaveClass(/selected/);
    
    // Tap on background
    await page.locator('[data-testid="canvas-background"]').tap({ position: { x: 100, y: 100 } });
    
    // Verify node is deselected
    await expect(page.locator('[data-testid="node-1"]')).not.toHaveClass(/selected/);
  });
});
```

### 2. Double Tap - Node Editing

```typescript
test('should open node editor on double tap @mobile', async ({ page }) => {
  await page.goto('/canvas/test-canvas');
  
  const node = page.locator('[data-testid="node-1"]');
  
  // Double tap on node
  await node.dblclick();
  
  // Verify editor modal appears
  await expect(page.locator('[data-testid="node-editor-modal"]')).toBeVisible();
  
  // Verify keyboard appears (mobile)
  const isMobile = page.viewportSize()!.width < 768;
  if (isMobile) {
    await expect(page.locator('input[data-testid="node-label"]')).toBeFocused();
  }
});
```

### 3. Pinch Zoom

```typescript
test('should zoom canvas on pinch gesture @mobile', async ({ page }) => {
  await page.goto('/canvas/test-canvas');
  
  const canvas = page.locator('[data-testid="canvas-container"]');
  
  // Get initial zoom level
  const initialZoom = await canvas.evaluate((el) => {
    return parseFloat(el.style.transform.match(/scale\(([^)]+)\)/)?.[1] || '1');
  });
  
  // Simulate pinch zoom (zoom in)
  await page.touchscreen.tap(200, 200);
  await page.touchscreen.tap(400, 400);
  
  // Simulate pinch gesture
  await canvas.evaluate((el) => {
    const event = new TouchEvent('touchstart', {
      touches: [
        { clientX: 200, clientY: 200 } as Touch,
        { clientX: 400, clientY: 400 } as Touch,
      ],
    });
    el.dispatchEvent(event);
  });
  
  // Move fingers apart (zoom in)
  await canvas.evaluate((el) => {
    const event = new TouchEvent('touchmove', {
      touches: [
        { clientX: 150, clientY: 150 } as Touch,
        { clientX: 450, clientY: 450 } as Touch,
      ],
    });
    el.dispatchEvent(event);
  });
  
  // End gesture
  await canvas.evaluate((el) => {
    el.dispatchEvent(new TouchEvent('touchend'));
  });
  
  // Verify zoom increased
  const finalZoom = await canvas.evaluate((el) => {
    return parseFloat(el.style.transform.match(/scale\(([^)]+)\)/)?.[1] || '1');
  });
  
  expect(finalZoom).toBeGreaterThan(initialZoom);
});
```

### 4. Pan/Drag

```typescript
test('should pan canvas on drag gesture @mobile', async ({ page }) => {
  await page.goto('/canvas/test-canvas');
  
  const canvas = page.locator('[data-testid="canvas-container"]');
  
  // Get initial position
  const initialTransform = await canvas.evaluate((el) => el.style.transform);
  
  // Perform drag gesture
  await page.touchscreen.tap(300, 300);
  await page.mouse.down();
  await page.mouse.move(100, 100);
  await page.mouse.up();
  
  // Verify canvas moved
  const finalTransform = await canvas.evaluate((el) => el.style.transform);
  expect(finalTransform).not.toBe(initialTransform);
});

test('should drag node to new position @mobile', async ({ page }) => {
  await page.goto('/canvas/test-canvas');
  
  const node = page.locator('[data-testid="node-1"]');
  
  // Get initial position
  const initialBox = await node.boundingBox();
  
  // Long press to start drag
  await node.tap({ delay: 500 });
  
  // Drag to new position
  await page.touchscreen.tap(initialBox!.x + 100, initialBox!.y + 100);
  
  // Verify node moved
  const finalBox = await node.boundingBox();
  expect(finalBox!.x).toBeGreaterThan(initialBox!.x);
  expect(finalBox!.y).toBeGreaterThan(initialBox!.y);
});
```

### 5. Swipe Gestures

```typescript
test('should navigate between canvas layers on swipe @mobile', async ({ page }) => {
  await page.goto('/canvas/test-canvas');
  
  const canvas = page.locator('[data-testid="canvas-container"]');
  
  // Verify on layer 1
  await expect(page.locator('[data-testid="layer-indicator"]')).toHaveText('Layer 1');
  
  // Swipe left to next layer
  await canvas.evaluate((el) => {
    const event = new TouchEvent('touchstart', {
      touches: [{ clientX: 400, clientY: 300 } as Touch],
    });
    el.dispatchEvent(event);
  });
  
  await canvas.evaluate((el) => {
    const event = new TouchEvent('touchmove', {
      touches: [{ clientX: 100, clientY: 300 } as Touch],
    });
    el.dispatchEvent(event);
  });
  
  await canvas.evaluate((el) => {
    el.dispatchEvent(new TouchEvent('touchend'));
  });
  
  // Verify on layer 2
  await expect(page.locator('[data-testid="layer-indicator"]')).toHaveText('Layer 2');
});
```

---

## Responsive Layout Tests

### Viewport Tests

```typescript
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
      
      expect(box!.width).toBeGreaterThan(viewport.width * 0.9);
      expect(box!.height).toBeGreaterThan(viewport.height * 0.7);
      
      // Verify toolbar is visible
      await expect(page.locator('[data-testid="mobile-toolbar"]')).toBeVisible();
      
      // Take screenshot for visual regression
      await page.screenshot({ 
        path: `screenshots/canvas-${viewport.name.toLowerCase().replace(' ', '-')}.png`,
        fullPage: true 
      });
    });
  }
});
```

### Orientation Tests

```typescript
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
  expect(toolbarBox!.y).toBeLessThan(100); // Should be at top in landscape
});
```

---

## Performance Tests

### Touch Responsiveness

```typescript
test('should respond to touch within 100ms @mobile @performance', async ({ page }) => {
  await page.goto('/canvas/test-canvas');
  
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
```

### Scroll Performance

```typescript
test('should maintain 60fps during scroll @mobile @performance', async ({ page }) => {
  await page.goto('/canvas/large-canvas'); // Canvas with many nodes
  
  // Start performance monitoring
  await page.evaluate(() => {
    (window as any).performanceData = [];
    let lastTime = performance.now();
    
    function measureFrame() {
      const currentTime = performance.now();
      const delta = currentTime - lastTime;
      (window as any).performanceData.push(delta);
      lastTime = currentTime;
      requestAnimationFrame(measureFrame);
    }
    
    requestAnimationFrame(measureFrame);
  });
  
  // Perform scroll
  await page.mouse.wheel(0, 1000);
  await page.waitForTimeout(1000);
  
  // Check frame times
  const frameData = await page.evaluate(() => (window as any).performanceData);
  const avgFrameTime = frameData.reduce((a: number, b: number) => a + b, 0) / frameData.length;
  const fps = 1000 / avgFrameTime;
  
  expect(fps).toBeGreaterThan(55); // Allow some variance from 60fps
});
```

---

## Accessibility Tests

### Touch Target Size

```typescript
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
```

### Screen Reader Support

```typescript
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
```

---

## Edge Cases

### Multi-Touch Conflicts

```typescript
test('should handle simultaneous touches gracefully @mobile', async ({ page }) => {
  await page.goto('/canvas/test-canvas');
  
  // Simulate two fingers touching different nodes
  await page.evaluate(() => {
    const canvas = document.querySelector('[data-testid="canvas-container"]') as HTMLElement;
    const event = new TouchEvent('touchstart', {
      touches: [
        { clientX: 100, clientY: 100 } as Touch,
        { clientX: 300, clientY: 300 } as Touch,
      ],
    });
    canvas.dispatchEvent(event);
  });
  
  // Should not crash or behave unexpectedly
  await expect(page.locator('[data-testid="canvas-container"]')).toBeVisible();
});
```

### Touch During Animation

```typescript
test('should handle touch during zoom animation @mobile', async ({ page }) => {
  await page.goto('/canvas/test-canvas');
  
  // Start zoom animation
  await page.locator('[data-testid="zoom-in-button"]').tap();
  
  // Immediately try to interact
  await page.locator('[data-testid="node-1"]').tap();
  
  // Should complete both actions without errors
  await expect(page.locator('[data-testid="node-1"]')).toHaveClass(/selected/);
});
```

---

## Test Execution

### Run Mobile Tests

```bash
# Run all mobile tests
pnpm test:mobile

# Run specific device
pnpm playwright test --project="Mobile Chrome"

# Run with UI mode for debugging
pnpm playwright test --ui --project="Mobile Safari"

# Generate test report
pnpm playwright show-report
```

### CI Integration

```yaml
# .github/workflows/mobile-canvas-tests.yml
name: Mobile Canvas Tests

on:
  pull_request:
    paths:
      - 'products/yappc/frontend/libs/canvas/**'
      - 'products/yappc/frontend/apps/web/src/routes/app/canvas/**'

jobs:
  mobile-tests:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: 20
      
      - name: Install dependencies
        run: pnpm install
      
      - name: Install Playwright browsers
        run: pnpm exec playwright install --with-deps
      
      - name: Run mobile tests
        run: pnpm test:mobile
      
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: mobile-test-results
          path: test-results/
```

---

## Performance Benchmarks

### Target Metrics

| Metric | Target | Acceptable |
|--------|--------|------------|
| Touch Response Time | <50ms | <100ms |
| Scroll FPS | 60fps | >55fps |
| Pinch Zoom FPS | 60fps | >50fps |
| Node Drag FPS | 60fps | >55fps |
| Initial Load Time | <2s | <3s |
| Touch Target Size | ≥44px | ≥40px |

---

## Debugging Tips

### Enable Touch Visualization

```typescript
// Add to test setup
await page.evaluate(() => {
  document.addEventListener('touchstart', (e) => {
    e.touches.forEach((touch) => {
      const dot = document.createElement('div');
      dot.style.position = 'fixed';
      dot.style.left = `${touch.clientX - 5}px`;
      dot.style.top = `${touch.clientY - 5}px`;
      dot.style.width = '10px';
      dot.style.height = '10px';
      dot.style.borderRadius = '50%';
      dot.style.backgroundColor = 'red';
      dot.style.zIndex = '9999';
      document.body.appendChild(dot);
      
      setTimeout(() => dot.remove(), 1000);
    });
  });
});
```

### Record Touch Events

```typescript
await page.evaluate(() => {
  (window as any).touchLog = [];
  ['touchstart', 'touchmove', 'touchend'].forEach(eventType => {
    document.addEventListener(eventType, (e) => {
      (window as any).touchLog.push({
        type: eventType,
        touches: Array.from((e as TouchEvent).touches).map(t => ({
          x: t.clientX,
          y: t.clientY
        })),
        timestamp: Date.now()
      });
    });
  });
});

// Later, retrieve logs
const touchLog = await page.evaluate(() => (window as any).touchLog);
console.log('Touch events:', touchLog);
```

---

## Next Steps

1. ✅ Create mobile test suite
2. ✅ Add to CI pipeline
3. ⏳ Run baseline tests
4. ⏳ Fix any failing tests
5. ⏳ Add visual regression
6. ⏳ Monitor performance metrics
7. ⏳ Iterate based on results

---

## References

- [Playwright Touch API](https://playwright.dev/docs/api/class-touchscreen)
- [Mobile Testing Best Practices](https://playwright.dev/docs/emulation)
- [Touch Events Specification](https://www.w3.org/TR/touch-events/)
- [WCAG Touch Target Guidelines](https://www.w3.org/WAI/WCAG21/Understanding/target-size.html)
