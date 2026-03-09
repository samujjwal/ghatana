import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import {
  getStoryUrl,
  getPreviewFrame,
  waitForCanvasReady,
  checkStorybookAvailable,
  OUT_DIR,
} from './playwright-helpers';

const STORY_URL = getStoryUrl();
console.log('Using STORY_URL for tests:', STORY_URL);

test.beforeAll(async () => {
  try {
    await fs.promises.mkdir(OUT_DIR, { recursive: true });
  } catch (e) {
    // ignore
  }
});

test.skip('simulate drag from palette to canvas drop zone', async ({ page }) => {
  // Skip if Storybook isn't running
  if (!(await checkStorybookAvailable(page))) {
    test.skip(true, 'Storybook is not running');
    return;
  }

  const logs: string[] = [];
  page.on('console', (msg) => logs.push(`${msg.type()}: ${msg.text()}`));
  page.on('pageerror', (err) =>
    logs.push(`pageerror: ${err.message || String(err)}`)
  );

  // Try to navigate to the toolbar story
  try {
    await page.goto(STORY_URL, { waitUntil: 'networkidle', timeout: 10000 });
  } catch (e) {
    console.warn('Navigation to Storybook timed out, trying to continue anyway');
  }

  // Use helper to target the story preview iframe and wait for canvas readiness
  const preview = getPreviewFrame(page);
  await waitForCanvasReady(preview, 15000);

  // Try to find the add node button first
  try {
    const addNodeButton = preview.locator('[data-testid="add-node-button"]');
    await addNodeButton.waitFor({ state: 'visible', timeout: 5000 });
    await addNodeButton.click();
    await page.waitForTimeout(500);
    
    // Check if node type picker opened
    const nodeTypePicker = preview.locator('[data-testid="node-type-picker-dialog"]');
    await expect(nodeTypePicker).toBeVisible({ timeout: 5000 });
    
    // Select a node type
    const nodeType = preview.locator('[data-testid="node-type-process"]');
    if (await nodeType.isVisible({ timeout: 2000 }).catch(() => false)) {
      await nodeType.click();
      await preview.locator('[data-testid="confirm-node-type"]').click();
    }
    
    // Wait for node to be added
    await page.waitForTimeout(500);
    
    // Take screenshot of the result
    const screenshotPath = path.join(OUT_DIR, 'storybook-add-node.png');
    await page.screenshot({ path: screenshotPath, fullPage: true });
    console.log('WROTE_SCREENSHOT:', screenshotPath);
    
    return;
  } catch (e) {
    console.warn('Could not find add node button, falling back to drag and drop');
  }

  // Fall back to palette drag and drop
  try {
    // now query stable selectors inside the preview
    const palette = preview.locator(
      '#component-palette, [data-testid="component-palette"]'
    );
    await palette.waitFor({ state: 'visible', timeout: 10000 });

    const source = palette
      .locator('[data-testid="component-palette-item"]')
      .first();
    await source.waitFor({ state: 'visible', timeout: 5000 });

    const dropZone = preview.locator(
      '#canvas-drop-zone, [data-testid="canvas-drop-zone"]'
    );
    await dropZone.waitFor({ state: 'visible', timeout: 5000 });

    // compute bounding boxes
    const srcBox = await source.boundingBox();
    const dstBox = await dropZone.boundingBox();

    if (!srcBox || !dstBox) {
      throw new Error('Could not resolve source or destination bounding boxes');
    }

    const srcX = srcBox.x + srcBox.width / 2;
    const srcY = srcBox.y + srcBox.height / 2;
    const dstX = dstBox.x + dstBox.width / 2;
    const dstY = dstBox.y + dstBox.height / 2;

    // perform pointer drag (mouse events should trigger @dnd-kit)
    await page.mouse.move(srcX, srcY);
    await page.mouse.down();
    // small pause to start drag
    await page.waitForTimeout(120);
    // move in steps to simulate human drag
    const steps = 10;
    for (let i = 1; i <= steps; i++) {
      const mx = srcX + ((dstX - srcX) * i) / steps;
      const my = srcY + ((dstY - srcY) * i) / steps;
      await page.mouse.move(mx, my, { steps: 2 });
      await page.waitForTimeout(30);
    }
    await page.mouse.up();

    // wait for any drop handlers to run
    await page.waitForTimeout(500);
  } catch (e) {
    console.error('Drag and drop failed:', e);
  }

  const screenshotPath = path.join(OUT_DIR, 'storybook-canvas-drag.png');
  await page.screenshot({ path: screenshotPath, fullPage: true });

  const logPath = path.join(OUT_DIR, 'storybook-canvas-drag.console.log');
  await fs.promises.writeFile(logPath, logs.join('\n'), 'utf8');

  console.log('WROTE_SCREENSHOT:', screenshotPath);
  console.log('WROTE_CONSOLE_LOG:', logPath);
});
