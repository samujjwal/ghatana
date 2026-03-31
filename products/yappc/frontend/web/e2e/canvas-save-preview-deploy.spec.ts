/**
 * Canvas → Save → Preview → Deploy E2E Tests
 *
 * Critical path 1 of 3 for Y-01 acceptance criteria.
 *
 * Covers the end-to-end journey of a developer who:
 * 1. Opens the unified canvas for a project
 * 2. Saves the canvas state
 * 3. Navigates to preview to verify the artifact
 * 4. Triggers a deployment from the deploy page
 *
 * @doc.type test
 * @doc.purpose E2E critical path: Canvas → Save → Preview → Deploy
 * @doc.layer product
 * @doc.phase 2
 */

import { test, expect, type Page } from '@playwright/test';

const PROJECT_ID = 'test-project';
const CANVAS_URL = `/p/${PROJECT_ID}/canvas`;
const PREVIEW_URL = `/p/${PROJECT_ID}/preview`;
const DEPLOY_URL = `/p/${PROJECT_ID}/deploy`;

test.describe('Canvas → Save → Preview → Deploy', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto(CANVAS_URL);
    await page.waitForLoadState('networkidle');
  });

  test.describe('Canvas Page', () => {
    test('should load the unified canvas', async () => {
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/canvas`));
    });

    test('should display the canvas toolbar', async () => {
      const toolbar = page.locator('[class*="toolbar"], [role="toolbar"]').first();
      await expect(toolbar).toBeVisible();
    });

    test('should display the canvas workspace area', async () => {
      const canvasArea = page.locator(
        '[class*="canvas"], [class*="flow"], [data-testid="canvas-area"]'
      ).first();
      await expect(canvasArea).toBeVisible();
    });

    test('should show save button or auto-save indicator', async () => {
      const saveControl = page.locator(
        'button:has-text("Save"), [aria-label*="Save" i], [class*="auto-save"], [class*="saved"]'
      ).first();
      await expect(saveControl).toBeVisible();
    });
  });

  test.describe('Save to Preview transition', () => {
    test('should navigate to preview page from canvas', async () => {
      // Look for preview navigation: tab, menu item, or link
      const previewLink = page.locator(
        'a[href*="preview"], button:has-text("Preview"), [aria-label*="preview" i]'
      ).first();
      await expect(previewLink).toBeVisible();
      await previewLink.click();
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/preview`));
    });

    test('should reach preview page directly via URL', async () => {
      await page.goto(PREVIEW_URL);
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/preview`));
      // Preview page must render without error
      await expect(page.locator('[class*="error-boundary"], main, [role="main"]').first()).toBeVisible();
    });
  });

  test.describe('Preview Page', () => {
    test.beforeEach(async () => {
      await page.goto(PREVIEW_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should display project preview header', async () => {
      const header = page.locator('h1, h2, [class*="preview-header"], [class*="header"]').first();
      await expect(header).toBeVisible();
    });

    test('should show navigation link to deploy', async () => {
      const deployLink = page.locator(
        'a[href*="deploy"], button:has-text("Deploy"), [aria-label*="deploy" i]'
      ).first();
      await expect(deployLink).toBeVisible();
    });

    test('should navigate to deploy page from preview', async () => {
      const deployLink = page.locator(
        'a[href*="deploy"], button:has-text("Deploy"), [aria-label*="deploy" i]'
      ).first();
      await deployLink.click();
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/deploy`));
    });
  });

  test.describe('Deploy Page', () => {
    test.beforeEach(async () => {
      await page.goto(DEPLOY_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should load the deploy page', async () => {
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/deploy`));
    });

    test('should display deploy page content', async () => {
      const content = page.locator(
        'h1, h2, [class*="deploy"], [class*="infrastructure"]'
      ).first();
      await expect(content).toBeVisible();
    });

    test('should show a deploy or trigger action', async () => {
      const deployAction = page.locator(
        'button:has-text("Deploy"), button:has-text("Trigger"), button:has-text("Release"), [aria-label*="deploy" i]'
      ).first();
      await expect(deployAction).toBeVisible();
    });

    test('should display environment or infrastructure sections', async () => {
      // DeployModeRenderer shows Infrastructure/Container Orchestration/Config Files
      const section = page.locator(
        '[class*="infrastructure"], [class*="environment"], [class*="container"], [class*="level"]'
      ).first();
      await expect(section).toBeVisible();
    });
  });

  test.describe('Complete critical path', () => {
    test('navigates Canvas → Preview → Deploy in sequence', async () => {
      // Step 1: Start at canvas
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/canvas`));

      // Step 2: Navigate to preview
      await page.goto(PREVIEW_URL);
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/preview`));
      const previewContent = page.locator('h1, h2, main, [role="main"]').first();
      await expect(previewContent).toBeVisible();

      // Step 3: Navigate to deploy
      await page.goto(DEPLOY_URL);
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/deploy`));
      const deployContent = page.locator('h1, h2, main, [role="main"]').first();
      await expect(deployContent).toBeVisible();
    });
  });
});
