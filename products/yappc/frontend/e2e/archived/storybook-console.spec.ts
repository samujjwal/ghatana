import { test } from '@playwright/test';
import fs from 'fs';
import path from 'path';

const STORY_URL =
  'http://localhost:6006/?path=/story/canvas-canvasscene--empty';
const OUT_DIR = path.resolve(process.cwd(), 'test-results');
const FALLBACK_URL = '/canvas';

test.beforeAll(async () => {
  try {
    await fs.promises.mkdir(OUT_DIR, { recursive: true });
  } catch (e) {
    // ignore
  }
});

test('capture CanvasScene story console + screenshot', async ({ page }) => {
  const logs: string[] = [];

  page.on('console', (msg) => {
    logs.push(`${msg.type()}: ${msg.text()}`);
  });

  page.on('pageerror', (err) => {
    logs.push(`pageerror: ${err.message || String(err)}`);
  });

  // navigate to storybook story, fall back to /canvas if unavailable
  let loadedStory = true;
  try {
    await page.goto(STORY_URL, { waitUntil: 'networkidle', timeout: 5000 });
  } catch (error) {
    loadedStory = false;
    await page.addInitScript(() => {
      try {
        (window as unknown).__E2E_TEST_MODE = true;
        (window as unknown).__E2E_TEST_NO_POINTER_BLOCK = true;
      } catch (e) {
        // ignore
      }
    });
    await page.goto(FALLBACK_URL, { waitUntil: 'networkidle' });
    logs.push(`warning: fell back to ${FALLBACK_URL} due to Storybook load failure`);
  }

  // wait briefly for runtime scripts to run and potential errors to appear
  await page.waitForTimeout(1500);

  const screenshotPath = path.join(OUT_DIR, 'storybook-canvas-empty.png');
  await page.screenshot({ path: screenshotPath, fullPage: true });

  const logPath = path.join(OUT_DIR, 'storybook-canvas-empty.console.log');
  await fs.promises.writeFile(logPath, logs.join('\n'), 'utf8');

  console.log('WROTE_SCREENSHOT:', screenshotPath);
  console.log('WROTE_CONSOLE_LOG:', logPath);
  console.log('CONSOLE_OUTPUT_START');
  console.log(logs.join('\n'));
  console.log('CONSOLE_OUTPUT_END');
});
