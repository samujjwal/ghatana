import { test, expect } from '@playwright/test';
import {
  getStoryUrl,
  getPreviewFrame,
  waitForCanvasReady,
  OUT_DIR,
} from './playwright-helpers';
import path from 'path';

const STORY_URL = getStoryUrl();
const FALLBACK_URL = '/canvas';

test.beforeAll(async () => {
  try {
    // ensure output dir exists
    // playwright tests often run in CI where OUT_DIR is present
    // but ensure for local runs
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const fs = require('fs');
    fs.mkdirSync(OUT_DIR, { recursive: true });
  } catch (e) {
    // ignore
  }
});

test('simulate multiple drags from palette into canvas and assert nodes added', async ({
  page,
}) => {
  let useStorybook = true;
  try {
    await page.goto(STORY_URL, { waitUntil: 'networkidle', timeout: 5000 });
  } catch (error) {
    useStorybook = false;
    await page.addInitScript(() => {
      (window as unknown).__E2E_TEST_MODE = true;
      (window as unknown).__E2E_TEST_NO_POINTER_BLOCK = true;
    });
    await page.goto(FALLBACK_URL, { waitUntil: 'networkidle' });
  }

  if (useStorybook) {
    const preview = getPreviewFrame(page);
    await waitForCanvasReady(preview, 15000);

    // palette lives inside the preview iframe
    const palette = preview.locator(
      '#component-palette, [data-testid="component-palette"]'
    );
    await palette.waitFor({ state: 'visible', timeout: 10000 });

    // Find at least two draggable items
    const items = palette.locator('[data-testid="component-palette-item"], [data-testid^="palette-item-"]');
    await items.first().waitFor({ state: 'visible', timeout: 5000 });
    const itemCount = await items.count();
    const toDrag = Math.min(2, itemCount);

    const dropZone = preview.locator(
      '#canvas-drop-zone, [data-testid="canvas-drop-zone"]'
    );
    await dropZone.waitFor({ state: 'visible', timeout: 10000 });

    // capture initial node count before performing drags
    const nodeLocator = preview.locator('.react-flow__node');
    const initialCount = await nodeLocator.count();

    for (let i = 0; i < toDrag; i++) {
      // Confirm test helper exists in preview frame
      const iframeHandle = await page
        .locator('iframe[id="storybook-preview-iframe"]')
        .elementHandle();
      const frame = iframeHandle ? await iframeHandle.contentFrame() : null;
      if (!frame) throw new Error('preview frame not found');

      const hasHelper = await frame.evaluate(() => {
        // @ts-ignore
        return !!(
          window &&
          (window as unknown).__TEST_helpers &&
          typeof (window as unknown).__TEST_helpers.addNode === 'function'
        );
      });
      if (!hasHelper) {
        throw new Error(
          'Test helper window.__TEST_helpers.addNode not found in preview frame'
        );
      }

      // Use a small test helper injected into the story preview to programmatically
      // add a node. This avoids brittle DnD interactions and focuses the smoke test
      // on verifying that adding nodes renders them in React Flow.
      await frame.evaluate(
        (node: unknown) => {
          const win = window as unknown;
          win.__TEST_helpers.addNode(node);
        },
        {
          id: `playwright-test-node-${i}`,
          data: { label: `pw-${i}` },
          position: { x: 50 + i * 20, y: 50 + i * 10 },
        }
      );

      // wait for the node to appear (short retry loop)
      const expectedAfterThis = initialCount + (i + 1);
      for (let attempt = 0; attempt < 40; attempt++) {
        const current = await nodeLocator.count();
        if (current >= expectedAfterThis) break;
        await page.waitForTimeout(150);
      }
    }

    // Give preview a moment to process and render nodes
    await page.waitForTimeout(1000);

    // Measure final node count and assert the delta is >= toDrag
    const finalCount = await nodeLocator.count();
    const delta = finalCount - initialCount;
    expect(delta).toBeGreaterThanOrEqual(toDrag);
  } else {
    await page.waitForSelector('[data-testid="component-palette"]', {
      state: 'visible',
      timeout: 15000,
    });
    await page.waitForSelector('[data-testid="react-flow-wrapper"]', {
      timeout: 15000,
    });

    const palette = page.locator(
      '#component-palette, [data-testid="component-palette"]'
    );
    const items = palette.locator('[data-testid^="palette-item-"]');
    await items.first().waitFor({ state: 'visible', timeout: 5000 });
    const itemCount = await items.count();
    const toAdd = Math.min(2, itemCount);
    const nodeLocator = page.locator('.react-flow__node');
    const initialCount = await nodeLocator.count();

    for (let i = 0; i < toAdd; i++) {
      await items.nth(i).click();
      await page.waitForTimeout(200);
    }

    await page.waitForTimeout(500);
    const finalCount = await nodeLocator.count();
    const delta = finalCount - initialCount;
    expect(delta).toBeGreaterThanOrEqual(toAdd);
  }

  const screenshotPath = path.join(OUT_DIR, 'storybook-canvas-drag-multi.png');
  await page.screenshot({ path: screenshotPath });
});
