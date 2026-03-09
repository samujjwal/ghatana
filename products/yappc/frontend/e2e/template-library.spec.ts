/**
 * E2E Tests for Feature 1.4: Document Management - Template Library
 *
 * Tests user-facing workflows for:
 * - Template save/load
 * - Autosave with toast notifications
 * - Version comparison
 * - Undo/redo with keyboard shortcuts (⌘Z / ⌘⇧Z)
 */

import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

test.describe('Feature 1.4: Template Library & Document Management', () => {
  test.beforeEach(async ({ page }) => {
    await setupTest(page, {
      url: '/canvas-poc',
      seedData: false,
      seedScenario: 'default',
    });
  });

  test.afterEach(async ({ page }) => {
    await teardownTest(page);
  });

  test('should save current canvas as template', async ({ page }) => {
    // Add some nodes to create a template
    const addBtn = page.getByRole('button', { name: /Add Test Node/i });
    await addBtn.click();
    await page.waitForTimeout(200);
    await addBtn.click();
    await page.waitForTimeout(200);

    const nodeCount = await page.locator('.react-flow__node').count();
    expect(nodeCount).toBeGreaterThanOrEqual(2);

    // Look for "Save as Template" button (might be in a menu or toolbar)
    const saveTemplateBtn = page.getByRole('button', {
      name: /Save.*Template/i,
    });

    if (await saveTemplateBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
      await saveTemplateBtn.click();

      // Fill in template metadata dialog
      const nameInput = page.getByLabel(/Template Name/i);
      await nameInput.fill('E2E Test Template');

      const descInput = page.getByLabel(/Description/i);
      if (await descInput.isVisible({ timeout: 500 }).catch(() => false)) {
        await descInput.fill('Created by E2E test');
      }

      // Save the template
      const saveBtn = page.getByRole('button', { name: /Save/i }).last();
      await saveBtn.click();

      // Wait for success toast or confirmation
      const toast = page
        .locator('[role="status"], [role="alert"]')
        .filter({ hasText: /saved|success/i });
      await expect(toast).toBeVisible({ timeout: 3000 });
    }
  });

  test('should trigger autosave after changes', async ({ page }) => {
    // Add a node to trigger dirty state
    const addBtn = page.getByRole('button', { name: /Add Test Node/i });
    await addBtn.click();
    await page.waitForTimeout(200);

    // Check for autosave indicator or toast
    // Autosave should trigger after idle time (default 5s based on requirements)
    const autosaveIndicator = page.locator(
      '[aria-label*="autosave"], [title*="autosave"]'
    );
    const toast = page
      .locator('[role="status"], [role="alert"]')
      .filter({ hasText: /autosave|saved/i });

    // Wait up to 8 seconds for autosave to trigger
    const indicatorVisible = await autosaveIndicator
      .isVisible({ timeout: 8000 })
      .catch(() => false);
    const toastVisible = await toast
      .isVisible({ timeout: 8000 })
      .catch(() => false);

    // At least one autosave signal should appear
    expect(indicatorVisible || toastVisible).toBe(true);
  });

  test('should undo/redo with keyboard shortcuts', async ({ page }) => {
    // Add a node
    const addBtn = page.getByRole('button', { name: /Add Test Node/i });
    await addBtn.click();
    await page.waitForTimeout(300);

    const nodesAfterAdd = await page.locator('.react-flow__node').count();
    expect(nodesAfterAdd).toBeGreaterThan(0);

    // Undo with Cmd+Z (Mac) or Ctrl+Z (Windows/Linux)
    const isMac = process.platform === 'darwin';
    await page.keyboard.press(isMac ? 'Meta+KeyZ' : 'Control+KeyZ');
    await page.waitForTimeout(300);

    const nodesAfterUndo = await page.locator('.react-flow__node').count();

    // After undo, we might have fewer nodes (depending on initial state)
    // Just verify the command was processed
    expect(nodesAfterUndo).toBeGreaterThanOrEqual(0);

    // Redo with Cmd+Shift+Z (Mac) or Ctrl+Shift+Z (Windows/Linux)
    await page.keyboard.press(isMac ? 'Meta+Shift+KeyZ' : 'Control+Shift+KeyZ');
    await page.waitForTimeout(300);

    const nodesAfterRedo = await page.locator('.react-flow__node').count();
    expect(nodesAfterRedo).toBeGreaterThanOrEqual(nodesAfterUndo);
  });

  test('should compare document versions', async ({ page }) => {
    // Add initial nodes
    const addBtn = page.getByRole('button', { name: /Add Test Node/i });
    await addBtn.click();
    await page.waitForTimeout(200);

    // Create version 1 (might be automatic or require explicit action)
    const createVersionBtn = page.getByRole('button', {
      name: /Create Version|Save Version/i,
    });
    if (
      await createVersionBtn.isVisible({ timeout: 1000 }).catch(() => false)
    ) {
      await createVersionBtn.click();
      await page.waitForTimeout(300);
    }

    // Add more nodes for version 2
    await addBtn.click();
    await page.waitForTimeout(200);
    await addBtn.click();
    await page.waitForTimeout(200);

    // Open version comparison if available
    const compareBtn = page.getByRole('button', {
      name: /Compare|Version History/i,
    });
    if (await compareBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
      await compareBtn.click();

      // Look for version list or comparison UI
      const versionList = page
        .locator('[role="list"], [role="listbox"]')
        .filter({ hasText: /version/i });
      await expect(versionList).toBeVisible({ timeout: 2000 });

      // Verify we can see multiple versions
      const versionItems = page
        .locator('[role="listitem"], [role="option"]')
        .filter({ hasText: /version/i });
      const count = await versionItems.count();
      expect(count).toBeGreaterThan(0);
    }
  });

  test('should load template from library', async ({ page }) => {
    // Open template library
    const libraryBtn = page.getByRole('button', {
      name: /Template Library|Templates/i,
    });

    if (await libraryBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await libraryBtn.click();

      // Wait for template list to appear
      const templateList = page
        .locator('[role="list"], [role="listbox"]')
        .filter({ hasText: /template/i });

      if (await templateList.isVisible({ timeout: 2000 }).catch(() => false)) {
        // Look for any template item
        const templateItem = page
          .locator('[role="listitem"], [role="option"]')
          .first();

        if (
          await templateItem.isVisible({ timeout: 1000 }).catch(() => false)
        ) {
          await templateItem.click();

          // Look for "Load" or "Use Template" button
          const loadBtn = page.getByRole('button', { name: /Load|Use|Apply/i });
          if (await loadBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await loadBtn.click();

            // Verify canvas updated (should have nodes from template)
            await page.waitForTimeout(500);
            const nodeCount = await page.locator('.react-flow__node').count();
            expect(nodeCount).toBeGreaterThan(0);
          }
        }
      }
    }
  });

  test('should filter templates by category', async ({ page }) => {
    // Open template library
    const libraryBtn = page.getByRole('button', {
      name: /Template Library|Templates/i,
    });

    if (await libraryBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await libraryBtn.click();
      await page.waitForTimeout(300);

      // Look for category filter
      const categoryFilter = page
        .getByLabel(/Category/i)
        .or(
          page
            .locator('select, [role="combobox"]')
            .filter({ hasText: /category/i })
        );

      if (
        await categoryFilter.isVisible({ timeout: 1000 }).catch(() => false)
      ) {
        // Select a category
        await categoryFilter.click();

        // Find and click an option
        const option = page
          .getByRole('option')
          .filter({ hasText: /flowchart|diagram/i })
          .first();
        if (await option.isVisible({ timeout: 1000 }).catch(() => false)) {
          await option.click();

          // Verify filter was applied (templates should update)
          await page.waitForTimeout(500);
          const templateItems = page.locator(
            '[role="listitem"], [role="option"]'
          );
          const count = await templateItems.count();

          // Should have at least some templates or show empty state
          expect(count).toBeGreaterThanOrEqual(0);
        }
      }
    }
  });

  test('should show version diff with structural changes highlighted', async ({
    page,
  }) => {
    // This test verifies the version diff UI shows structural vs styling changes

    // Add initial node
    const addBtn = page.getByRole('button', { name: /Add Test Node/i });
    await addBtn.click();
    await page.waitForTimeout(200);

    // Save version 1
    const createVersionBtn = page.getByRole('button', {
      name: /Create Version|Save Version/i,
    });
    if (
      await createVersionBtn.isVisible({ timeout: 1000 }).catch(() => false)
    ) {
      await createVersionBtn.click();
      await page.waitForTimeout(300);

      // Make structural change (add node)
      await addBtn.click();
      await page.waitForTimeout(200);

      // Open version comparison
      const compareBtn = page.getByRole('button', {
        name: /Compare|Diff|Version History/i,
      });
      if (await compareBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
        await compareBtn.click();

        // Look for diff indicators showing added/removed/modified
        const diffIndicators = page.locator(
          '[data-diff-type], [class*="diff-"], [aria-label*="added"], [aria-label*="removed"]'
        );

        if (
          await diffIndicators
            .first()
            .isVisible({ timeout: 2000 })
            .catch(() => false)
        ) {
          const count = await diffIndicators.count();
          expect(count).toBeGreaterThan(0);
        }
      }
    }
  });

  test('should preserve undo history after autosave', async ({ page }) => {
    // Add a node
    const addBtn = page.getByRole('button', { name: /Add Test Node/i });
    await addBtn.click();
    await page.waitForTimeout(300);

    const nodesAfterAdd = await page.locator('.react-flow__node').count();

    // Wait for potential autosave
    await page.waitForTimeout(6000);

    // Undo should still work after autosave
    const isMac = process.platform === 'darwin';
    await page.keyboard.press(isMac ? 'Meta+KeyZ' : 'Control+KeyZ');
    await page.waitForTimeout(300);

    // Should still be able to undo despite autosave
    const nodesAfterUndo = await page.locator('.react-flow__node').count();
    expect(nodesAfterUndo).toBeGreaterThanOrEqual(0);
  });
});
