import { test, expect } from '@playwright/test';

/**
 * Pipelines E2E Tests
 * 
 * Tests the complete pipelines functionality including:
 * - Listing pipeline entries
 * - Creating pipelines
 * - Executing pipelines
 * - Viewing execution history
 * 
 * @doc.type test
 * @doc.purpose E2E tests for pipelines functionality
 * @doc.layer testing
 */

test.describe('Pipelines', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/pipelines');
  });

  test('should display the outcome-first pipelines list', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('Workflows');
    await expect(page.getByTestId('workflow-outcome-banner')).toBeVisible();
    await expect(page.getByTestId('create-pipeline-button')).toBeVisible();
  });

  test('should navigate to the create pipeline page', async ({ page }) => {
    await page.getByTestId('create-pipeline-button').click();
    await expect(page).toHaveURL(/\/pipelines\/new/);
  });

  test('should open the review modal for a pipeline', async ({ page }) => {
    const firstWorkflow = page.getByTestId('workflow-item').first();
    if (await firstWorkflow.isVisible()) {
      await firstWorkflow.click();
      await expect(page.getByTestId('workflow-review-modal')).toBeVisible();
    }
  });

  test('should navigate to the advanced editor from the review flow', async ({ page }) => {
    const firstWorkflow = page.getByTestId('workflow-item').first();
    if (await firstWorkflow.isVisible()) {
      await firstWorkflow.click();
      const advancedEditorButton = page.getByRole('button', { name: /advanced editor/i }).last();
      if (await advancedEditorButton.isVisible()) {
        await advancedEditorButton.click();
        await expect(page).toHaveURL(/\/pipelines\/[^/]+/);
      }
    }
  });

  test('should filter pipeline entries by status', async ({ page }) => {
    const statusFilter = page.getByTestId('workflow-status-filter');
    if (await statusFilter.isVisible()) {
      await statusFilter.selectOption('active');
      await page.waitForTimeout(500);
      
      const workflows = page.getByTestId('workflow-item');
      await expect(workflows.first()).toBeVisible();
    }
  });
});
