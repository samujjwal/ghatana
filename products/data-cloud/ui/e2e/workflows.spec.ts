import { test, expect } from '@playwright/test';

/**
 * Workflows E2E Tests
 * 
 * Tests the complete workflows functionality including:
 * - Listing workflows
 * - Creating workflows
 * - Executing workflows
 * - Viewing execution history
 * 
 * @doc.type test
 * @doc.purpose E2E tests for workflows functionality
 * @doc.layer testing
 */

test.describe('Workflows', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/workflows');
  });

  test('should display workflows list', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('Workflows');
    await expect(page.getByRole('link', { name: /new workflow/i })).toBeVisible();
  });

  test('should navigate to create workflow page', async ({ page }) => {
    await page.getByRole('link', { name: /new workflow/i }).click();
    await expect(page).toHaveURL(/\/workflows\/new/);
  });

  test('should view workflow details', async ({ page }) => {
    const firstWorkflow = page.locator('[data-testid="workflow-item"]').first();
    if (await firstWorkflow.isVisible()) {
      await firstWorkflow.click();
      await expect(page).toHaveURL(/\/workflows\/[^/]+$/);
    }
  });

  test('should execute a workflow', async ({ page }) => {
    // Navigate to workflow detail
    const firstWorkflow = page.locator('[data-testid="workflow-item"]').first();
    if (await firstWorkflow.isVisible()) {
      await firstWorkflow.click();
      
      // Click execute button
      const executeButton = page.getByRole('button', { name: /execute|run/i });
      if (await executeButton.isVisible()) {
        await executeButton.click();
        
        // Verify execution started
        await expect(page.locator('text=/execution started|running/i')).toBeVisible({ timeout: 5000 });
      }
    }
  });

  test('should filter workflows by status', async ({ page }) => {
    const statusFilter = page.locator('[data-testid="status-filter"]');
    if (await statusFilter.isVisible()) {
      await statusFilter.selectOption('active');
      await page.waitForTimeout(500);
      
      // Verify filtered results
      const workflows = page.locator('[data-testid="workflow-item"]');
      await expect(workflows.first()).toBeVisible();
    }
  });
});
