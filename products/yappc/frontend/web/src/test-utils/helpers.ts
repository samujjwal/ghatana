/**
 * @module helpers
 * @description Test helper functions for common testing patterns.
 */

import type { Page } from '@playwright/test';

/**
 * Get Storybook story URL
 */
export function getStoryUrl(
  componentName: string,
  storyName: string,
  options?: {
    viewMode?: 'story' | 'docs';
    args?: Record<string, unknown>;
  }
): string {
  const baseUrl = process.env.STORYBOOK_URL || 'http://localhost:6006';
  const viewMode = options?.viewMode || 'story';
  
  // Convert component name to Storybook path format
  const path = componentName.replace(/\//g, '-').toLowerCase();
  
  let url = `${baseUrl}/?path=/${viewMode}/${path}--${storyName.toLowerCase().replace(/\s+/g, '-')}`;
  
  // Add args if provided
  if (options?.args) {
    const argsParam = Object.entries(options.args)
      .map(([key, value]) => `${key}:${JSON.stringify(value)}`)
      .join(';');
    url += `&args=${encodeURIComponent(argsParam)}`;
  }
  
  return url;
}

/**
 * Wait for canvas to be ready in Playwright tests
 */
export async function waitForCanvasReady(
  page: Page,
  options?: {
    timeout?: number;
    selector?: string;
  }
): Promise<void> {
  const timeout = options?.timeout || 10000;
  const selector = options?.selector || '[data-testid="canvas-viewport"]';
  
  // Wait for canvas element to be visible
  await page.waitForSelector(selector, {
    state: 'visible',
    timeout,
  });
  
  // Wait for React Flow to initialize
  await page.waitForFunction(
    () => {
      const canvas = document.querySelector('[data-testid="canvas-viewport"]');
      return canvas && canvas.getAttribute('data-initialized') === 'true';
    },
    { timeout }
  );
  
  // Wait for any loading states to complete
  await page.waitForLoadState('networkidle', { timeout });
  
  // Additional wait for animations to settle
  await page.waitForTimeout(100);
}

/**
 * Wait for element to be stable (no more position changes)
 */
export async function waitForStableElement(
  page: Page,
  selector: string,
  options?: {
    timeout?: number;
    checkInterval?: number;
  }
): Promise<void> {
  const timeout = options?.timeout || 5000;
  const checkInterval = options?.checkInterval || 100;
  const startTime = Date.now();
  
  let lastPosition: { x: number; y: number } | null = null;
  let stableCount = 0;
  const requiredStableChecks = 3;
  
  while (Date.now() - startTime < timeout) {
    const boundingBox = await page.locator(selector).boundingBox();
    
    if (!boundingBox) {
      await page.waitForTimeout(checkInterval);
      continue;
    }
    
    if (lastPosition &&
        lastPosition.x === boundingBox.x &&
        lastPosition.y === boundingBox.y) {
      stableCount++;
      
      if (stableCount >= requiredStableChecks) {
        return;
      }
    } else {
      stableCount = 0;
    }
    
    lastPosition = { x: boundingBox.x, y: boundingBox.y };
    await page.waitForTimeout(checkInterval);
  }
  
  throw new Error(`Element ${selector} did not stabilize within ${timeout}ms`);
}

/**
 * Wait for canvas node to appear
 */
export async function waitForCanvasNode(
  page: Page,
  nodeId: string,
  options?: {
    timeout?: number;
  }
): Promise<void> {
  const timeout = options?.timeout || 5000;
  const selector = `[data-testid="canvas-node-${nodeId}"]`;
  
  await page.waitForSelector(selector, {
    state: 'visible',
    timeout,
  });
}

/**
 * Get canvas node count
 */
export async function getCanvasNodeCount(page: Page): Promise<number> {
  return page.locator('[data-testid^="canvas-node-"]').count();
}

/**
 * Get canvas edge count
 */
export async function getCanvasEdgeCount(page: Page): Promise<number> {
  return page.locator('[data-testid^="canvas-edge-"]').count();
}

/**
 * Drag element to position
 */
export async function dragToPosition(
  page: Page,
  sourceSelector: string,
  targetX: number,
  targetY: number
): Promise<void> {
  const source = page.locator(sourceSelector);
  
  // Get source position
  const sourceBox = await source.boundingBox();
  if (!sourceBox) {
    throw new Error(`Source element ${sourceSelector} not found`);
  }
  
  // Start drag
  await source.hover();
  await page.mouse.down();
  
  // Move to target
  await page.mouse.move(targetX, targetY, { steps: 10 });
  
  // Drop
  await page.mouse.up();
  
  // Wait for animation to complete
  await page.waitForTimeout(100);
}

/**
 * Drag palette item to canvas
 */
export async function dragPaletteItemToCanvas(
  page: Page,
  category: string,
  itemType: string,
  dropX: number,
  dropY: number
): Promise<void> {
  const paletteSelector = `[data-testid="canvas-palette-${category}-${itemType}"]`;
  await dragToPosition(page, paletteSelector, dropX, dropY);
}

/**
 * Take screenshot with timestamp
 */
export async function takeTimestampedScreenshot(
  page: Page,
  name: string
): Promise<string> {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const filename = `${name}-${timestamp}.png`;
  
  await page.screenshot({
    path: `test-results/screenshots/${filename}`,
    fullPage: true,
  });
  
  return filename;
}

/**
 * Wait for specific number of elements
 */
export async function waitForElementCount(
  page: Page,
  selector: string,
  count: number,
  options?: {
    timeout?: number;
  }
): Promise<void> {
  const timeout = options?.timeout || 5000;
  
  await page.waitForFunction(
    ({ selector, count }) => {
      return document.querySelectorAll(selector).length === count;
    },
    { selector, count },
    { timeout }
  );
}

/**
 * Get viewport state from canvas
 */
export async function getCanvasViewport(page: Page): Promise<{
  x: number;
  y: number;
  zoom: number;
}> {
  return page.evaluate(() => {
    const viewport = document.querySelector('[data-testid="canvas-viewport"]');
    if (!viewport) {
      throw new Error('Canvas viewport not found');
    }
    
    // @ts-expect-error - Accessing custom property
    return viewport.__viewport || { x: 0, y: 0, zoom: 1 };
  });
}

/**
 * Set viewport zoom level
 */
export async function setCanvasZoom(
  page: Page,
  zoom: number
): Promise<void> {
  await page.evaluate((zoomLevel) => {
    const viewport = document.querySelector('[data-testid="canvas-viewport"]');
    if (!viewport) {
      throw new Error('Canvas viewport not found');
    }
    
    // @ts-expect-error - Accessing custom method
    viewport.__setZoom?.(zoomLevel);
  }, zoom);
  
  await page.waitForTimeout(100);
}

/**
 * Click canvas controls button
 */
export async function clickCanvasControl(
  page: Page,
  action: 'zoom-in' | 'zoom-out' | 'fit-view' | 'reset'
): Promise<void> {
  const selector = `[data-testid="canvas-controls-${action}"]`;
  await page.click(selector);
  await page.waitForTimeout(100);
}

/**
 * Wait for network idle with retries
 */
export async function waitForNetworkIdle(
  page: Page,
  options?: {
    timeout?: number;
    retries?: number;
  }
): Promise<void> {
  const timeout = options?.timeout || 5000;
  const retries = options?.retries || 3;
  
  for (let i = 0; i < retries; i++) {
    try {
      await page.waitForLoadState('networkidle', { timeout });
      return;
    } catch (error) {
      if (i === retries - 1) throw error;
      await page.waitForTimeout(500);
    }
  }
}

/**
 * Check if element is visible in viewport
 */
export async function isElementInViewport(
  page: Page,
  selector: string
): Promise<boolean> {
  return page.evaluate((sel) => {
    const element = document.querySelector(sel);
    if (!element) return false;
    
    const rect = element.getBoundingClientRect();
    return (
      rect.top >= 0 &&
      rect.left >= 0 &&
      rect.bottom <= window.innerHeight &&
      rect.right <= window.innerWidth
    );
  }, selector);
}

/**
 * Measure element render time
 */
export async function measureRenderTime(
  page: Page,
  action: () => Promise<void>
): Promise<number> {
  const startTime = Date.now();
  await action();
  return Date.now() - startTime;
}

/**
 * Wait for selector with custom error message
 */
export async function waitForSelectorWithMessage(
  page: Page,
  selector: string,
  errorMessage: string,
  options?: {
    timeout?: number;
    state?: 'attached' | 'detached' | 'visible' | 'hidden';
  }
): Promise<void> {
  try {
    await page.waitForSelector(selector, {
      timeout: options?.timeout || 5000,
      state: options?.state || 'visible',
    });
  } catch (error) {
    throw new Error(`${errorMessage}: ${error}`);
  }
}
