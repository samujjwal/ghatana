import { Page, FrameLocator } from '@playwright/test';
import path from 'path';

/** Resolve the Storybook URL for tests. Prefer STORYBOOK_URL, fallback to STORYBOOK_PORT. */
export function getStoryUrl(
  pathname = '/?path=/story/canvas-toolbar--all-actions'
) {
  if (process.env.STORYBOOK_URL) return process.env.STORYBOOK_URL;
  const port = process.env.STORYBOOK_PORT || '6006';
  return `http://localhost:${port}${pathname}`;
}

/** Check if Storybook is running and accessible */
export async function checkStorybookAvailable(page: Page): Promise<boolean> {
  try {
    await page.goto(getStoryUrl(), { timeout: 5000 });
    return true;
  } catch (e) {
    console.warn('Storybook may not be running:', e);
    return false;
  }
}

/** Return a frameLocator for the Storybook preview iframe. */
export function getPreviewFrame(page: Page): FrameLocator {
  return page.frameLocator('iframe[id="storybook-preview-iframe"]');
}

/** Wait for the canvas to be ready inside the preview iframe. */
export async function waitForCanvasReady(frame: FrameLocator, timeout = 15000) {
  // Try multiple likely selectors for React Flow / canvas drop areas
  const selectors = [
    '[data-testid="rf__wrapper"]',
    '[data-testid="react-flow-wrapper"]',
    '#canvas-drop-zone',
    '[data-testid="canvas-drop-zone"]',
    '[data-testid="canvas-flow"]',
    '[data-testid="canvas-toolbar"]',
  ];
  
  // Log what we're looking for to help debug
  console.log('Waiting for canvas ready, checking selectors:', selectors.join(', '));
  
  for (const sel of selectors) {
    try {
      await frame.locator(sel).waitFor({ state: 'visible', timeout: timeout / selectors.length });
      console.log(`Found canvas element: ${sel}`);
      return sel;
    } catch (e) {
      // try next
    }
  }
  
  // last attempt: wait for any list item from the palette to appear
  try {
    await frame
      .locator('#component-palette, [data-testid="component-palette"]')
      .waitFor({ state: 'visible', timeout: 5000 });
    console.log('Found component palette');
    return '#component-palette';
  } catch (e) {
    console.warn('Could not find any canvas elements or palette');
    throw new Error('Canvas elements not found in preview frame');
  }
}

export const OUT_DIR = path.resolve(process.cwd(), 'test-results');
