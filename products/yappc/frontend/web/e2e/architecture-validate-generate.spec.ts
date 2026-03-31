/**
 * Architecture → Validate → Generate E2E Tests
 *
 * Critical path 2 of 3 for Y-01 acceptance criteria.
 *
 * Covers the end-to-end journey of a developer who:
 * 1. Opens the unified canvas architecture workspace
 * 2. Reviews the canvas state and triggers architecture validation
 * 3. Validates the design (Validate panel/tab in the canvas right panel)
 * 4. Generates code artifacts from the validated architecture
 *
 * The YAPPC unified canvas exposes Validate and Generate tabs in the
 * UnifiedRightPanel, driven by `onValidate` and `onGenerate` callbacks
 * wired from CanvasRoute → CanvasManager → CanvasToolbar.
 *
 * @doc.type test
 * @doc.purpose E2E critical path: Architecture → Validate → Generate
 * @doc.layer product
 * @doc.phase 2
 */

import { test, expect, type Page } from '@playwright/test';

const PROJECT_ID = 'test-project';
const CANVAS_URL = `/p/${PROJECT_ID}/canvas`;

test.describe('Architecture → Validate → Generate', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto(CANVAS_URL);
    await page.waitForLoadState('networkidle');
  });

  test.describe('Canvas Architecture Workspace', () => {
    test('should load the unified canvas page', async () => {
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/canvas`));
    });

    test('should display the canvas area', async () => {
      const canvasArea = page
        .locator('[class*="canvas"], [class*="flow"], [role="main"]')
        .first();
      await expect(canvasArea).toBeVisible();
    });

    test('should display the abstraction level navigation', async () => {
      // The canvas supports multiple abstraction levels: System, Component, File, Code
      const levelNav = page.locator(
        '[class*="level"], [class*="abstraction"], [aria-label*="level" i], button:has-text("System"), button:has-text("Component")'
      ).first();
      await expect(levelNav).toBeVisible();
    });

    test('should show a right panel or panel toggle button', async () => {
      const panelControl = page.locator(
        '[class*="panel"], [class*="right-panel"], button[aria-label*="panel" i], button:has-text("Guide"), button:has-text("Validate")'
      ).first();
      await expect(panelControl).toBeVisible();
    });
  });

  test.describe('Validate Step', () => {
    test('should expose a Validate control in the canvas toolbar or panel', async () => {
      // The Validate tab / button is in the UnifiedRightPanel (label "Validate")
      // or as a toolbar action exposed via onValidate → onValidationPanelToggle
      const validateControl = page.locator(
        'button:has-text("Validate"), [aria-label*="validate" i], [role="tab"]:has-text("Validate"), text=Validate'
      ).first();
      await expect(validateControl).toBeVisible();
    });

    test('should open the Validate panel when Validate is clicked', async () => {
      const validateControl = page
        .locator(
          'button:has-text("Validate"), [aria-label*="validate" i], [role="tab"]:has-text("Validate")'
        )
        .first();
      await validateControl.click();

      // Validate panel should appear
      const validatePanel = page.locator(
        '[class*="validate"], [class*="validation"], [aria-label*="validation" i]'
      ).first();
      await expect(validatePanel).toBeVisible({ timeout: 5000 });
    });

    test('should show validation score or results after validation', async () => {
      const validateControl = page
        .locator(
          'button:has-text("Validate"), [aria-label*="validate" i], [role="tab"]:has-text("Validate")'
        )
        .first();
      await validateControl.click();

      // Validation score, status, or result message should appear
      const validationResult = page.locator(
        '[class*="score"], [class*="validat"], text=/valid|score|check|warning|error/i'
      ).first();
      await expect(validationResult).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('Generate Step', () => {
    test('should expose a Generate control in the canvas toolbar or panel', async () => {
      const generateControl = page.locator(
        'button:has-text("Generate"), [aria-label*="generate" i], [role="tab"]:has-text("Generate"), text=Generate'
      ).first();
      await expect(generateControl).toBeVisible();
    });

    test('should open the Generate panel when Generate is clicked', async () => {
      const generateControl = page
        .locator(
          'button:has-text("Generate"), [aria-label*="generate" i], [role="tab"]:has-text("Generate")'
        )
        .first();
      await generateControl.click();

      // Code generation panel should appear
      const generatePanel = page.locator(
        '[class*="generat"], [class*="code-gen"], [aria-label*="generat" i]'
      ).first();
      await expect(generatePanel).toBeVisible({ timeout: 5000 });
    });

    test('should show file count or generation options after clicking Generate', async () => {
      const generateControl = page
        .locator(
          'button:has-text("Generate"), [aria-label*="generate" i], [role="tab"]:has-text("Generate")'
        )
        .first();
      await generateControl.click();

      // Generated file count, list, or trigger button should appear
      const generateContent = page.locator(
        '[class*="file-count"], [class*="generated"], button:has-text("Generate Code"), button:has-text("Run Generate")'
      ).first();
      await expect(generateContent).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe('Complete critical path', () => {
    test('Architecture → Validate → Generate sequence', async () => {
      // Step 1: Verify canvas (architecture workspace) is loaded
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/canvas`));
      const canvas = page.locator('[class*="canvas"], [class*="flow"], [role="main"]').first();
      await expect(canvas).toBeVisible();

      // Step 2: Open Validate panel
      const validateControl = page
        .locator('button:has-text("Validate"), [role="tab"]:has-text("Validate")')
        .first();
      await expect(validateControl).toBeVisible();
      await validateControl.click();

      // Wait for validation panel to appear
      const validationPanel = page
        .locator('[class*="validate"], [class*="validation"], [class*="score"]')
        .first();
      await expect(validationPanel).toBeVisible({ timeout: 10000 });

      // Step 3: Open Generate panel
      const generateControl = page
        .locator('button:has-text("Generate"), [role="tab"]:has-text("Generate")')
        .first();
      await expect(generateControl).toBeVisible();
      await generateControl.click();

      // Wait for generate panel to appear
      const generatePanel = page
        .locator('[class*="generat"], [class*="code-gen"]')
        .first();
      await expect(generatePanel).toBeVisible({ timeout: 10000 });
    });
  });
});
