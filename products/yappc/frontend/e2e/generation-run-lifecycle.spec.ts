/**
 * Full Prompt-to-Download E2E Test
 *
 * Covers the complete generation run lifecycle:
 * - Prompt capture
 * - Plan generation
 * - Confirm
 * - Generate
 * - Review
 * - Preview
 * - Export/Download
 * - Rollback
 * - Failure recovery
 *
 * @doc.type e2e-test
 * @doc.purpose Test complete generation run lifecycle from prompt to download
 * @doc.layer e2e
 */

import { test, expect } from '@playwright/test';

test.describe('Generation Run Lifecycle E2E', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the dashboard
    await page.goto('/');
    // Wait for authentication and workspace loading
    await page.waitForSelector('[data-testid="dashboard-container"]', { timeout: 10000 });
  });

  test('complete prompt-to-download happy path', async ({ page }) => {
    // TODO-018: Prompt capture
    await page.click('[data-testid="new-project-button"]');
    await page.fill('[data-testid="project-name-input"]', 'E2E Test Project');
    await page.fill('[data-testid="intent-prompt-textarea"]', 'Create a simple todo list application with CRUD operations');
    await page.click('[data-testid="create-project-button"]');
    
    // Verify project created and in INTENT phase
    await page.waitForSelector('[data-testid="project-phase-intent"]', { timeout: 10000 });
    expect(await page.textContent('[data-testid="project-phase-badge"]')).toContain('INTENT');

    // TODO-018: Plan generation
    await page.click('[data-testid="generate-plan-button"]');
    await page.waitForSelector('[data-testid="plan-generated"]', { timeout: 30000 });
    
    // Verify plan is generated
    const planContent = await page.textContent('[data-testid="plan-content"]');
    expect(planContent).toBeTruthy();
    expect(planContent?.length).toBeGreaterThan(0);

    // TODO-018: Confirm plan
    await page.click('[data-testid="confirm-plan-button"]');
    await page.waitForSelector('[data-testid="plan-confirmed"]', { timeout: 10000 });
    
    // Verify transition to GENERATE phase
    await page.waitForSelector('[data-testid="project-phase-generate"]', { timeout: 10000 });

    // TODO-018: Generate
    await page.click('[data-testid="start-generation-button"]');
    await page.waitForSelector('[data-testid="generation-complete"]', { timeout: 60000 });
    
    // Verify generation complete
    expect(await page.textContent('[data-testid="generation-status"]')).toContain('Complete');

    // TODO-018: Review diff
    await page.click('[data-testid="review-diff-button"]');
    await page.waitForSelector('[data-testid="diff-view"]', { timeout: 10000 });
    
    // Verify diff is displayed
    expect(await page.isVisible('[data-testid="diff-content"]')).toBeTruthy();

    // TODO-018: Preview session
    await page.click('[data-testid="start-preview-button"]');
    await page.waitForSelector('[data-testid="preview-iframe"]', { timeout: 15000 });
    
    // Verify preview is loaded
    const previewFrame = page.frameLocator('[data-testid="preview-iframe"]');
    await expect(previewFrame.locator('body')).toBeVisible();

    // TODO-018: Apply changes
    await page.click('[data-testid="apply-changes-button"]');
    await page.waitForSelector('[data-testid="changes-applied"]', { timeout: 10000 });
    
    // Verify changes applied
    expect(await page.textContent('[data-testid="apply-status"]')).toContain('Applied');

    // TODO-018: Export/download
    await page.click('[data-testid="export-button"]');
    await page.waitForSelector('[data-testid="export-modal"]', { timeout: 5000 });
    await page.click('[data-testid="download-button"]');
    
    // Verify download initiated
    const downloadPromise = page.waitForEvent('download');
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('todo-list');
  });

  test('rollback after apply', async ({ page }) => {
    // Create and generate project (abbreviated setup)
    await page.click('[data-testid="new-project-button"]');
    await page.fill('[data-testid="project-name-input"]', 'Rollback Test Project');
    await page.fill('[data-testid="intent-prompt-textarea"]', 'Create a simple counter app');
    await page.click('[data-testid="create-project-button"]');
    await page.waitForSelector('[data-testid="project-phase-intent"]', { timeout: 10000 });
    
    // Generate and apply
    await page.click('[data-testid="generate-plan-button"]');
    await page.waitForSelector('[data-testid="plan-generated"]', { timeout: 30000 });
    await page.click('[data-testid="confirm-plan-button"]');
    await page.waitForSelector('[data-testid="plan-confirmed"]', { timeout: 10000 });
    await page.click('[data-testid="start-generation-button"]');
    await page.waitForSelector('[data-testid="generation-complete"]', { timeout: 60000 });
    await page.click('[data-testid="apply-changes-button"]');
    await page.waitForSelector('[data-testid="changes-applied"]', { timeout: 10000 });

    // TODO-018: Rollback
    await page.click('[data-testid="rollback-button"]');
    await page.waitForSelector('[data-testid="rollback-modal"]', { timeout: 5000 });
    await page.fill('[data-testid="rollback-reason"]', 'Test rollback');
    await page.click('[data-testid="confirm-rollback-button"]');
    
    // Verify rollback complete
    await page.waitForSelector('[data-testid="rollback-complete"]', { timeout: 10000 });
    expect(await page.textContent('[data-testid="rollback-status"]')).toContain('Rolled Back');
  });

  test('failure recovery during generation', async ({ page }) => {
    // Create project
    await page.click('[data-testid="new-project-button"]');
    await page.fill('[data-testid="project-name-input"]', 'Failure Recovery Test');
    await page.fill('[data-testid="intent-prompt-textarea"]', 'Create a complex app that will fail');
    await page.click('[data-testid="create-project-button"]');
    await page.waitForSelector('[data-testid="project-phase-intent"]', { timeout: 10000 });

    // Start generation that will fail
    await page.click('[data-testid="generate-plan-button"]');
    await page.waitForSelector('[data-testid="plan-generated"]', { timeout: 30000 });
    await page.click('[data-testid="confirm-plan-button"]');
    await page.waitForSelector('[data-testid="plan-confirmed"]', { timeout: 10000 });
    await page.click('[data-testid="start-generation-button"]');

    // Wait for failure
    await page.waitForSelector('[data-testid="generation-failed"]', { timeout: 60000 });

    // TODO-018: Failure recovery - retry
    await page.click('[data-testid="retry-generation-button"]');
    await page.waitForSelector('[data-testid="generation-complete"]', { timeout: 60000 });

    // Verify recovery successful
    expect(await page.textContent('[data-testid="generation-status"]')).toContain('Complete');
  });

  test('review reject flow', async ({ page }) => {
    // Create and generate project
    await page.click('[data-testid="new-project-button"]');
    await page.fill('[data-testid="project-name-input"]', 'Review Reject Test');
    await page.fill('[data-testid="intent-prompt-textarea"]', 'Create a test app');
    await page.click('[data-testid="create-project-button"]');
    await page.waitForSelector('[data-testid="project-phase-intent"]', { timeout: 10000 });
    await page.click('[data-testid="generate-plan-button"]');
    await page.waitForSelector('[data-testid="plan-generated"]', { timeout: 30000 });
    await page.click('[data-testid="confirm-plan-button"]');
    await page.waitForSelector('[data-testid="plan-confirmed"]', { timeout: 10000 });
    await page.click('[data-testid="start-generation-button"]');
    await page.waitForSelector('[data-testid="generation-complete"]', { timeout: 60000 });

    // Review and reject
    await page.click('[data-testid="review-diff-button"]');
    await page.waitForSelector('[data-testid="diff-view"]', { timeout: 10000 });
    await page.fill('[data-testid="review-comments"]', 'Changes not acceptable, needs revision');
    await page.click('[data-testid="reject-button"]');
    await page.waitForSelector('[data-testid="review-rejected"]', { timeout: 10000 });

    // Verify rejection and rollback to plan phase
    await page.waitForSelector('[data-testid="project-phase-plan"]', { timeout: 10000 });
    expect(await page.textContent('[data-testid="review-status"]')).toContain('Rejected');
  });

  test('export blocked before review complete', async ({ page }) => {
    // Create and generate project without review
    await page.click('[data-testid="new-project-button"]');
    await page.fill('[data-testid="project-name-input"]', 'Export Block Test');
    await page.fill('[data-testid="intent-prompt-textarea"]', 'Create a test app');
    await page.click('[data-testid="create-project-button"]');
    await page.waitForSelector('[data-testid="project-phase-intent"]', { timeout: 10000 });
    await page.click('[data-testid="generate-plan-button"]');
    await page.waitForSelector('[data-testid="plan-generated"]', { timeout: 30000 });
    await page.click('[data-testid="confirm-plan-button"]');
    await page.waitForSelector('[data-testid="plan-confirmed"]', { timeout: 10000 });
    await page.click('[data-testid="start-generation-button"]');
    await page.waitForSelector('[data-testid="generation-complete"]', { timeout: 60000 });

    // Try to export without review
    await page.click('[data-testid="export-button"]');
    
    // TODO-018: Verify export blocked
    await page.waitForSelector('[data-testid="export-blocked-error"]', { timeout: 5000 });
    expect(await page.textContent('[data-testid="export-error-message"]')).toContain('review must be complete');
  });
});
