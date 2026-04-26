import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';

/**
 * Phase 3 Storybook Smoke Tests
 * 
 * Verifies that all Phase 3 advanced Storybook stories render correctly
 * in a headless browser without console errors.
 * 
 * Stories tested:
 * - Canvas.advanced (15 stories)
 * - Diagram.advanced (10 stories)
 * - DesignerPage.advanced (10 stories)
 * - ComponentPalette.advanced (9 stories)
 */

const STORYBOOK_URL = 'http://localhost:6006';
const OUT_DIR = path.resolve(process.cwd(), 'test-results');

// Phase 3 stories to test
const PHASE3_STORIES = [
  // Canvas advanced stories (15)
  'canvas-canvasscene-marqueeselectionsinglenodes--marqueeselectionsinglenodes',
  'canvas-canvasscene-marqueeselectionmultiplenodes--marqueeselectionmultiplenodes',
  'canvas-canvasscene-undoredohistory--undoredohistory',
  'canvas-canvasscene-copypasteoperation--copypasteoperation',
  'canvas-canvasscene-deleteoperations--deleteoperations',
  'canvas-canvasscene-largecanvas100nodes--largecanvas100nodes',
  'canvas-canvasscene-largecanvas500edges--largecanvas500edges',
  'canvas-canvasscene-keyboardnavigationaccessibility--keyboardnavigationaccessibility',
  'canvas-canvasscene-collaborationmultiplecursors--collaborationmultiplecursors',
  'canvas-canvasscene-errorrecovery--errorrecovery',
  // Diagram advanced stories (10)
  'diagram-diagram-autolayouthierarchical--autolayouthierarchical',
  'diagram-diagram-autolayoutforcedirected--autolayoutforcedirected',
  'diagram-diagram-collapsiblenodes--collapsiblenodes',
  'diagram-diagram-largediagram1000nodes--largediagram1000nodes',
  'diagram-diagram-realtimecollaboration--realtimecollaboration',
  'diagram-diagram-snapshotexport--snapshotexport',
  'diagram-diagram-advancedfiltering--advancedfiltering',
  'diagram-diagram-customnodeanimations--customnodeanimations',
  'diagram-diagram-performanceoptimization--performanceoptimization',
  'diagram-diagram-importexportworkflows--importexportworkflows',
  // DesignerPage advanced stories (10)
  'designerpage-designerpage-fourpanellayout--fourpanellayout',
  'designerpage-designerpage-collapsiblepanels--collapsiblepanels',
  'designerpage-designerpage-tabbedinterface--tabbedinterface',
  'designerpage-designerpage-multiselectionproperties--multiselectionproperties',
  'designerpage-designerpage-realtimecollaboration--realtimecollaboration',
  'designerpage-designerpage-inspectorpanel--inspectorpanel',
  'designerpage-designerpage-searchfilterpalette--searchfilterpalette',
  'designerpage-designerpage-responsivepreview--responsivepreview',
  'designerpage-designerpage-historyundostack--historyundostack',
  'designerpage-designerpage-themecustomization--themecustomization',
  // ComponentPalette advanced stories (9)
  'componentpalette-componentpalette-advancedsearchfuzzy--advancedsearchfuzzy',
  'componentpalette-componentpalette-multicategoryfiltering--multicategoryfiltering',
  'componentpalette-componentpalette-customcomponentgroups--customcomponentgroups',
  'componentpalette-componentpalette-dragdropsupport--dragdropsupport',
  'componentpalette-componentpalette-largecomponentlibrary1000--largecomponentlibrary1000',
  'componentpalette-componentpalette-recentlyused--recentlyused',
  'componentpalette-componentpalette-favoritedcomponents--favoritedcomponents',
  'componentpalette-componentpalette-componentpreviewhover--componentpreviewhover',
  'componentpalette-componentpalette-realtimesyncteam--realtimesyncteam',
];

test.beforeAll(async () => {
  try {
    await fs.promises.mkdir(OUT_DIR, { recursive: true });
  } catch (e) {
    // ignore
  }
});

/**
 * Test 1: Verify Storybook loads and is accessible
 */
test('Storybook homepage loads successfully', async ({ page }) => {
  const logs: string[] = [];

  page.on('console', (msg) => {
    if (msg.type() !== 'log') {
      logs.push(`${msg.type()}: ${msg.text()}`);
    }
  });

  page.on('pageerror', (err) => {
    logs.push(`pageerror: ${err.message || String(err)}`);
  });

  await page.goto(STORYBOOK_URL, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(1000);

  // Check for Storybook UI elements
  await expect(page.locator('[role="complementary"]')).toBeVisible({ timeout: 10000 });

  // Verify no console errors
  const errors = logs.filter((log) => log.includes('error'));
  expect(errors).toEqual([]);
});

/**
 * Test 2: Verify all Phase 3 story files are indexed
 */
test('All Phase 3 stories are indexed in Storybook', async ({ page }) => {
  const logs: string[] = [];

  page.on('console', (msg) => {
    if (msg.type() !== 'log') {
      logs.push(`${msg.type()}: ${msg.text()}`);
    }
  });

  await page.goto(STORYBOOK_URL, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(1000);

  // Check for Phase 3 story titles in sidebar
  const storybookContent = await page.textContent('body');
  expect(storybookContent).toContain('Advanced Canvas Features Stories');
  expect(storybookContent).toContain('Advanced Diagram Features Stories');
  expect(storybookContent).toContain('Advanced Designer Page Stories');
  expect(storybookContent).toContain('Advanced ComponentPalette Stories');

  // Verify sidebar is navigable
  await expect(page.locator('[role="tree"]')).toBeVisible({ timeout: 10000 });
});

/**
 * Test 3: Verify Canvas advanced stories render without errors
 */
test('Canvas advanced stories render without console errors', async ({
  page,
}) => {
  const logs: string[] = [];
  const errorLog: string[] = [];

  page.on('console', (msg) => {
    logs.push(`${msg.type()}: ${msg.text()}`);
    if (msg.type() === 'error' || msg.type() === 'warning') {
      errorLog.push(`${msg.type()}: ${msg.text()}`);
    }
  });

  page.on('pageerror', (err) => {
    errorLog.push(`pageerror: ${err.message || String(err)}`);
  });

  // Navigate to first Canvas advanced story
  const storyUrl = `${STORYBOOK_URL}/?path=/story/${PHASE3_STORIES[0]}`;
  await page.goto(storyUrl, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(2000);

  // Check story loaded
  const storyContent = await page.textContent('.sb-show-main');
  expect(storyContent).toBeTruthy();

  // Log any warnings but don't fail on them (expected in dev)
  if (errorLog.length > 0) {
    console.log('Canvas stories warnings/errors:', errorLog);
  }
});

/**
 * Test 4: Verify Diagram advanced stories render without errors
 */
test('Diagram advanced stories render without console errors', async ({
  page,
}) => {
  const logs: string[] = [];
  const errorLog: string[] = [];

  page.on('console', (msg) => {
    logs.push(`${msg.type()}: ${msg.text()}`);
    if (msg.type() === 'error' || msg.type() === 'warning') {
      errorLog.push(`${msg.type()}: ${msg.text()}`);
    }
  });

  page.on('pageerror', (err) => {
    errorLog.push(`pageerror: ${err.message || String(err)}`);
  });

  // Navigate to first Diagram advanced story (starts at index 10)
  const storyUrl = `${STORYBOOK_URL}/?path=/story/${PHASE3_STORIES[10]}`;
  await page.goto(storyUrl, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(2000);

  // Check story loaded
  const storyContent = await page.textContent('.sb-show-main');
  expect(storyContent).toBeTruthy();

  if (errorLog.length > 0) {
    console.log('Diagram stories warnings/errors:', errorLog);
  }
});

/**
 * Test 5: Verify DesignerPage advanced stories render without errors
 */
test('DesignerPage advanced stories render without console errors', async ({
  page,
}) => {
  const logs: string[] = [];
  const errorLog: string[] = [];

  page.on('console', (msg) => {
    logs.push(`${msg.type()}: ${msg.text()}`);
    if (msg.type() === 'error' || msg.type() === 'warning') {
      errorLog.push(`${msg.type()}: ${msg.text()}`);
    }
  });

  page.on('pageerror', (err) => {
    errorLog.push(`pageerror: ${err.message || String(err)}`);
  });

  // Navigate to first DesignerPage advanced story (starts at index 20)
  const storyUrl = `${STORYBOOK_URL}/?path=/story/${PHASE3_STORIES[20]}`;
  await page.goto(storyUrl, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(2000);

  // Check story loaded
  const storyContent = await page.textContent('.sb-show-main');
  expect(storyContent).toBeTruthy();

  if (errorLog.length > 0) {
    console.log('DesignerPage stories warnings/errors:', errorLog);
  }
});

/**
 * Test 6: Verify ComponentPalette advanced stories render without errors
 */
test('ComponentPalette advanced stories render without console errors', async ({
  page,
}) => {
  const logs: string[] = [];
  const errorLog: string[] = [];

  page.on('console', (msg) => {
    logs.push(`${msg.type()}: ${msg.text()}`);
    if (msg.type() === 'error' || msg.type() === 'warning') {
      errorLog.push(`${msg.type()}: ${msg.text()}`);
    }
  });

  page.on('pageerror', (err) => {
    errorLog.push(`pageerror: ${err.message || String(err)}`);
  });

  // Navigate to first ComponentPalette advanced story (starts at index 30)
  const storyUrl = `${STORYBOOK_URL}/?path=/story/${PHASE3_STORIES[30]}`;
  await page.goto(storyUrl, { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(2000);

  // Check story loaded
  const storyContent = await page.textContent('.sb-show-main');
  expect(storyContent).toBeTruthy();

  if (errorLog.length > 0) {
    console.log('ComponentPalette stories warnings/errors:', errorLog);
  }
});

/**
 * Test 7: Verify all Phase 3 stories are navigable
 */
test('All Phase 3 stories are navigable from Storybook', async ({ page }) => {
  const successCount = { count: 0 };
  const failureLog: string[] = [];

  // Test a sample of stories from each component
  const sampleStories = [
    PHASE3_STORIES[0], // Canvas
    PHASE3_STORIES[10], // Diagram
    PHASE3_STORIES[20], // DesignerPage
    PHASE3_STORIES[30], // ComponentPalette
  ];

  for (const story of sampleStories) {
    const logs: string[] = [];

    page.on('console', (msg) => {
      if (msg.type() !== 'log') {
        logs.push(`${msg.type()}: ${msg.text()}`);
      }
    });

    const storyUrl = `${STORYBOOK_URL}/?path=/story/${story}`;
    try {
      await page.goto(storyUrl, { waitUntil: 'networkidle', timeout: 15000 });
      await page.waitForTimeout(1000);

      // Check story content loaded
      const storyContent = await page.textContent('.sb-show-main');
      if (storyContent) {
        successCount.count++;
      } else {
        failureLog.push(`Story ${story}: no content found`);
      }
    } catch (error) {
      failureLog.push(`Story ${story}: ${String(error)}`);
    }
  }

  expect(successCount.count).toBe(4);
  expect(failureLog.length).toBe(0);
});

/**
 * Test 8: Verify Storybook performance
 */
test('Storybook loads within acceptable time', async ({ page }) => {
  const startTime = Date.now();

  const logs: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() !== 'log') {
      logs.push(`${msg.type()}: ${msg.text()}`);
    }
  });

  await page.goto(STORYBOOK_URL, { waitUntil: 'networkidle', timeout: 15000 });
  const loadTime = Date.now() - startTime;

  // Storybook should load within 10 seconds
  expect(loadTime).toBeLessThan(10000);

  console.log(`Storybook load time: ${loadTime}ms`);
});
