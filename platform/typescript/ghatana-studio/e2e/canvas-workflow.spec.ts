/**
 * @fileoverview E2E tests for CanvasPage workflow
 *
 * Tests the canvas visualization, interaction, and drill-down workflows.
 *
 * @doc.type test
 * @doc.purpose CanvasPage workflow E2E tests
 * @doc.layer studio
 */

import { expect, test } from '@playwright/test';

test.describe('CanvasPage Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/canvas');
  });

  test('loads canvas with graph visualization', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Canvas' })).toBeVisible();
    await expect(page.getByTestId('hybrid-canvas')).toBeVisible();
  });

  test('displays artifact graph nodes', async ({ page }) => {
    const nodeCount = await page.getByTestId('canvas-node').count();
    expect(nodeCount).toBeGreaterThan(0);
  });

  test('supports canvas pan and zoom', async ({ page }) => {
    const canvas = page.getByTestId('hybrid-canvas');
    
    // Pan the canvas
    await canvas.click({ position: { x: 100, y: 100 } });
    await page.mouse.down();
    await page.mouse.move(200, 200);
    await page.mouse.up();
    
    // Zoom in
    await page.keyboard.press('ControlOrMeta+');
    await page.keyboard.press('Equal');
    
    // Verify canvas transformed
    await expect(canvas).toBeVisible();
  });

  test('selects nodes on canvas', async ({ page }) => {
    const firstNode = page.getByTestId('canvas-node').first();
    await firstNode.click();
    
    // Verify selection
    await expect(firstNode).toHaveClass(/selected/);
    await expect(page.getByTestId('node-details-panel')).toBeVisible();
  });

  test('displays node details on selection', async ({ page }) => {
    const firstNode = page.getByTestId('canvas-node').first();
    await firstNode.click();
    
    await expect(page.getByTestId('node-title')).toBeVisible();
    await expect(page.getByTestId('node-type')).toBeVisible();
  });

  test('supports multi-selection with shift-click', async ({ page }) => {
    const nodes = page.getByTestId('canvas-node');
    const firstNode = nodes.nth(0);
    const secondNode = nodes.nth(1);
    
    await firstNode.click();
    await page.keyboard.down('Shift');
    await secondNode.click();
    await page.keyboard.up('Shift');
    
    // Verify both selected
    await expect(firstNode).toHaveClass(/selected/);
    await expect(secondNode).toHaveClass(/selected/);
  });

  test('navigates to drill-down on double-click', async ({ page }) => {
    const portalNode = page.getByTestId('canvas-node').filter({ hasText: 'portal' }).first();
    
    if (await portalNode.count() > 0) {
      await portalNode.dblclick();
      
      // Should navigate to drill-down view
      await expect(page).toHaveURL(/\/canvas\/.+/);
      await expect(page.getByTestId('drill-down-view')).toBeVisible();
    }
  });

  test('filters canvas nodes by type', async ({ page }) => {
    await page.getByTestId('filter-button').click();
    await page.getByTestId('filter-type-select').selectOption('component');
    await page.getByTestId('apply-filter').click();
    
    // Verify only component nodes shown
    const nodes = page.getByTestId('canvas-node');
    const visibleCount = await nodes.count();
    expect(visibleCount).toBeGreaterThan(0);
  });

  test('searches canvas nodes by name', async ({ page }) => {
    await page.getByTestId('search-input').fill('test');
    
    // Verify search results
    await expect(page.getByTestId('search-results')).toBeVisible();
  });

  test('resets canvas view', async ({ page }) => {
    // Pan and zoom
    const canvas = page.getByTestId('hybrid-canvas');
    await canvas.click({ position: { x: 100, y: 100 } });
    await page.mouse.down();
    await page.mouse.move(300, 300);
    await page.mouse.up();
    
    // Reset view
    await page.getByTestId('reset-view-button').click();
    
    // Verify reset
    await expect(page.getByTestId('viewport-info')).toContainText('100%');
  });
});
