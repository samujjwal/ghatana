/**
 * Generation Run Lifecycle E2E Tests
 *
 * Full prompt-to-download E2E test covering the complete generation run lifecycle:
 * 1. Prompt capture (Intent phase)
 * 2. Plan generation (Plan phase)
 * 3. Confirm plan (Confirm phase)
 * 4. Generate run (Generate phase)
 * 5. Review diff (Review phase)
 * 6. Preview session (Preview phase)
 * 7. Apply/Reject/Rollback (Apply phase)
 * 8. Export/Download (Export phase)
 * 9. Failure recovery (Error handling)
 *
 * @doc.type test
 * @doc.purpose E2E critical path: Prompt → Plan → Generate → Review → Preview → Export
 * @doc.layer product
 * @doc.phase 2
 * @doc.pattern E2E Test
 */

import { test, expect, type Page } from '@playwright/test';

const PROJECT_ID = 'test-project';
const INTENT_URL = `/p/${PROJECT_ID}/intent`;
const SHAPE_URL = `/p/${PROJECT_ID}/shape`;
const GENERATE_URL = `/p/${PROJECT_ID}/generate`;
const PREVIEW_URL = `/p/${PROJECT_ID}/preview`;
const RUN_URL = `/p/${PROJECT_ID}/run`;

test.describe('Generation Run Lifecycle', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    // Start at intent phase
    await page.goto(INTENT_URL);
    await page.waitForLoadState('networkidle');
  });

  test.describe('Intent Phase - Prompt Capture', () => {
    test('should load intent phase', async () => {
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/intent`));
    });

    test('should display prompt input area', async () => {
      const promptInput = page.locator('textarea[placeholder*="prompt" i], [data-testid="prompt-input"], textarea').first();
      await expect(promptInput).toBeVisible();
    });

    test('should show generate plan button', async () => {
      const generateButton = page.locator('button:has-text("Generate"), button:has-text("Plan"), [aria-label*="generate" i]').first();
      await expect(generateButton).toBeVisible();
    });

    test('should capture user prompt', async () => {
      const promptInput = page.locator('textarea').first();
      const testPrompt = 'Create a full-stack e-commerce application with product catalog and checkout';
      
      await promptInput.fill(testPrompt);
      await expect(promptInput).toHaveValue(testPrompt);
    });
  });

  test.describe('Plan Generation Phase', () => {
    test.beforeEach(async () => {
      // Enter a prompt and trigger plan generation
      const promptInput = page.locator('textarea').first();
      await promptInput.fill('Create a full-stack e-commerce application');
      
      const generateButton = page.locator('button:has-text("Generate"), button:has-text("Plan")').first();
      await generateButton.click();
      // Wait for plan generation to complete
      await page.waitForTimeout(2000);
    });

    test('should display generated plan', async () => {
      const planDisplay = page.locator('[class*="plan"], [data-testid="plan"], [class*="generated"]').first();
      await expect(planDisplay).toBeVisible();
    });

    test('should show plan summary', async () => {
      const summary = page.locator('[class*="summary"], [data-testid="plan-summary"]').first();
      await expect(summary).toBeVisible();
    });

    test('should display confirm plan button', async () => {
      const confirmButton = page.locator('button:has-text("Confirm"), button:has-text("Approve")').first();
      await expect(confirmButton).toBeVisible();
    });

    test('should allow plan modification before confirm', async () => {
      const editButton = page.locator('button:has-text("Edit"), button:has-text("Modify")').first();
      if (await editButton.isVisible()) {
        await editButton.click();
        const planEditor = page.locator('[contenteditable="true"], textarea').first();
        await expect(planEditor).toBeVisible();
      }
    });
  });

  test.describe('Generate Run Phase', () => {
    test.beforeEach(async () => {
      // Navigate to generate phase
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should load generate phase', async () => {
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/generate`));
    });

    test('should display generate action button', async () => {
      const generateButton = page.locator('button:has-text("Generate"), button:has-text("Run")').first();
      await expect(generateButton).toBeVisible();
    });

    test('should start generation run', async () => {
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      
      // Wait for generation to start
      await page.waitForTimeout(1000);
      
      // Check for progress indicator
      const progress = page.locator('[class*="progress"], [role="progressbar"]').first();
      await expect(progress).toBeVisible({ timeout: 5000 });
    });

    test('should display generation status', async () => {
      const status = page.locator('[class*="status"], [data-testid="generation-status"]').first();
      await expect(status).toBeVisible();
    });

    test('should show run ID after generation starts', async () => {
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      await page.waitForTimeout(2000);
      
      const runId = page.locator('[class*="run-id"], [data-testid="run-id"]').first();
      await expect(runId).toBeVisible();
    });
  });

  test.describe('Review Diff Phase', () => {
    test.beforeEach(async () => {
      // Navigate to generate phase and start generation
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
      
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      
      // Wait for generation to complete and review to be ready
      await page.waitForTimeout(5000);
    });

    test('should display diff review interface', async () => {
      const diffView = page.locator('[class*="diff"], [data-testid="diff-review"]').first();
      await expect(diffView).toBeVisible({ timeout: 10000 });
    });

    test('should show apply and reject buttons', async () => {
      const applyButton = page.locator('button:has-text("Apply"), button:has-text("Accept")').first();
      const rejectButton = page.locator('button:has-text("Reject"), button:has-text("Discard")').first();
      
      await expect(applyButton).toBeVisible({ timeout: 10000 });
      await expect(rejectButton).toBeVisible({ timeout: 10000 });
    });

    test('should display artifact changes', async () => {
      const changesList = page.locator('[class*="changes"], [data-testid="artifact-changes"]').first();
      await expect(changesList).toBeVisible({ timeout: 10000 });
    });

    test('should allow diff review decision', async () => {
      const applyButton = page.locator('button:has-text("Apply")').first();
      await applyButton.click();
      
      // Wait for decision to be recorded
      await page.waitForTimeout(1000);
      
      // Check for confirmation or success message
      const success = page.locator('[class*="success"], [class*="applied"]').first();
      await expect(success).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe('Preview Session Phase', () => {
    test.beforeEach(async () => {
      await page.goto(PREVIEW_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should load preview phase', async () => {
      await expect(page).toHaveURL(new RegExp(`p/${PROJECT_ID}/preview`));
    });

    test('should display preview session controls', async () => {
      const previewControls = page.locator('[class*="preview-controls"], [data-testid="preview-controls"]').first();
      await expect(previewControls).toBeVisible();
    });

    test('should show preview iframe or container', async () => {
      const previewFrame = page.locator('iframe, [class*="preview-frame"], [data-testid="preview-frame"]').first();
      await expect(previewFrame).toBeVisible();
    });

    test('should display preview session metadata', async () => {
      const metadata = page.locator('[class*="metadata"], [data-testid="preview-metadata"]').first();
      await expect(metadata).toBeVisible();
    });
  });

  test.describe('Apply Phase', () => {
    test.beforeEach(async () => {
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should apply approved changes', async () => {
      // Start generation
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      await page.waitForTimeout(5000);
      
      // Apply changes
      const applyButton = page.locator('button:has-text("Apply")').first();
      await applyButton.click();
      await page.waitForTimeout(2000);
      
      // Check for apply success indicator
      const applySuccess = page.locator('[class*="applied"], [class*="success"]').first();
      await expect(applySuccess).toBeVisible({ timeout: 10000 });
    });

    test('should reject changes', async () => {
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      await page.waitForTimeout(5000);
      
      const rejectButton = page.locator('button:has-text("Reject")').first();
      await rejectButton.click();
      await page.waitForTimeout(1000);
      
      // Check for rejection confirmation
      const rejection = page.locator('[class*="rejected"], [class*="discarded"]').first();
      await expect(rejection).toBeVisible();
    });
  });

  test.describe('Rollback Phase', () => {
    test.beforeEach(async () => {
      await page.goto(RUN_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should display rollback option', async () => {
      const rollbackButton = page.locator('button:has-text("Rollback"), [aria-label*="rollback" i]').first();
      await expect(rollbackButton).toBeVisible();
    });

    test('should execute rollback', async () => {
      const rollbackButton = page.locator('button:has-text("Rollback")').first();
      await rollbackButton.click();
      
      // Wait for rollback confirmation dialog
      await page.waitForTimeout(500);
      
      const confirmButton = page.locator('button:has-text("Confirm"), button:has-text("Yes")').first();
      if (await confirmButton.isVisible()) {
        await confirmButton.click();
      }
      
      await page.waitForTimeout(2000);
      
      // Check for rollback success
      const rollbackSuccess = page.locator('[class*="rolled-back"], [class*="reverted"]').first();
      await expect(rollbackSuccess).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('Export/Download Phase', () => {
    test.beforeEach(async () => {
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should display export button', async () => {
      const exportButton = page.locator('button:has-text("Export"), button:has-text("Download")').first();
      await expect(exportButton).toBeVisible();
    });

    test('should show export format options', async () => {
      const exportButton = page.locator('button:has-text("Export")').first();
      await exportButton.click();
      
      const formatOptions = page.locator('[class*="format"], [data-testid="export-formats"]').first();
      await expect(formatOptions).toBeVisible();
    });

    test('should download artifacts', async () => {
      // Setup download handler
      const downloadPromise = page.waitForEvent('download');
      
      const exportButton = page.locator('button:has-text("Export")').first();
      await exportButton.click();
      
      const downloadButton = page.locator('button:has-text("Download"), button:has-text("Save")').first();
      await downloadButton.click();
      
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toBeTruthy();
    });

    test('should block export before review is complete', async () => {
      // Try to export before generation and review
      const exportButton = page.locator('button:has-text("Export")').first();
      await exportButton.click();
      
      // Should show error or warning about incomplete review
      const error = page.locator('[class*="error"], [class*="warning"], [role="alert"]').first();
      await expect(error).toBeVisible();
    });
  });

  test.describe('Failure Recovery', () => {
    test('should handle generation failure gracefully', async () => {
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
      
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      
      // Wait for potential failure
      await page.waitForTimeout(3000);
      
      // Check for error display
      const errorDisplay = page.locator('[class*="error"], [class*="failed"], [role="alert"]').first();
      if (await errorDisplay.isVisible()) {
        // Should show retry button
        const retryButton = page.locator('button:has-text("Retry"), button:has-text("Try Again")').first();
        await expect(retryButton).toBeVisible();
      }
    });

    test('should provide retry mechanism', async () => {
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
      
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      await page.waitForTimeout(3000);
      
      const retryButton = page.locator('button:has-text("Retry")').first();
      if (await retryButton.isVisible()) {
        await retryButton.click();
        
        // Should restart generation
        const progress = page.locator('[class*="progress"]').first();
        await expect(progress).toBeVisible({ timeout: 5000 });
      }
    });

    test('should show correlation ID on failure', async () => {
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
      
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      await page.waitForTimeout(3000);
      
      const errorDisplay = page.locator('[class*="error"]').first();
      if (await errorDisplay.isVisible()) {
        const correlationId = page.locator('[class*="correlation"], [data-testid="correlation-id"]').first();
        await expect(correlationId).toBeVisible();
      }
    });
  });

  test.describe('Complete End-to-End Flow', () => {
    test('full prompt-to-download lifecycle', async () => {
      // Step 1: Capture prompt
      await page.goto(INTENT_URL);
      await page.waitForLoadState('networkidle');
      
      const promptInput = page.locator('textarea').first();
      await promptInput.fill('Create a simple todo list application');
      
      const generatePlanButton = page.locator('button:has-text("Generate"), button:has-text("Plan")').first();
      await generatePlanButton.click();
      await page.waitForTimeout(3000);
      
      // Step 2: Confirm plan
      const confirmButton = page.locator('button:has-text("Confirm"), button:has-text("Approve")').first();
      if (await confirmButton.isVisible()) {
        await confirmButton.click();
      }
      await page.waitForTimeout(2000);
      
      // Step 3: Generate
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
      
      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      await page.waitForTimeout(5000);
      
      // Step 4: Review and apply
      const applyButton = page.locator('button:has-text("Apply")').first();
      if (await applyButton.isVisible()) {
        await applyButton.click();
        await page.waitForTimeout(2000);
      }
      
      // Step 5: Preview
      await page.goto(PREVIEW_URL);
      await page.waitForLoadState('networkidle');
      
      const previewFrame = page.locator('iframe, [class*="preview-frame"]').first();
      await expect(previewFrame).toBeVisible();
      
      // Step 6: Export
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');
      
      const exportButton = page.locator('button:has-text("Export")').first();
      await expect(exportButton).toBeVisible();
    });
  });
});
