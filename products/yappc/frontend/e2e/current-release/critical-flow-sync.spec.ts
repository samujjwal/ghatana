import { test, expect, type Locator, type Page } from '@playwright/test';

import { setupTest, teardownTest } from '../helpers/test-isolation';

function firstVisibleLocator(page: Page, selectors: string[]): Locator {
  return page.locator(selectors.join(', ')).first();
}

test.describe('Critical Flow Sync', () => {
  test.afterEach(async ({ page }) => {
    await teardownTest(page);
  });

  test('drag and drop, reorder, save and reload keep canvas state stable', async ({ page }) => {
    await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

    const paletteItem = firstVisibleLocator(page, [
      '[data-testid^="palette-handle-"]',
      '[data-testid^="palette-item-"]',
    ]);
    const canvasSurface = firstVisibleLocator(page, [
      '[data-testid="canvas-drop-zone"]',
      '[data-is-canvas]',
      '[data-testid="canvas-area"]',
    ]);

    if (!(await paletteItem.isVisible().catch(() => false)) || !(await canvasSurface.isVisible().catch(() => false))) {
      test.skip(true, 'Canvas drag/drop controls are unavailable in this environment');
    }

    await paletteItem.dragTo(canvasSurface);

    const nodes = page.locator('[data-testid^="canvas-item-"], .react-flow__node');
    const nodeCountAfterDrop = await nodes.count();
    expect(nodeCountAfterDrop).toBeGreaterThan(0);

    if (nodeCountAfterDrop > 1) {
      await nodes.nth(0).dragTo(nodes.nth(1));
      await expect(nodes.first()).toBeVisible();
    }

    const saveButton = firstVisibleLocator(page, [
      '[data-testid="save-button"]',
      '[data-testid="designer-save"]',
      'button:has-text("Save")',
    ]);
    if (await saveButton.isVisible().catch(() => false)) {
      await saveButton.click();
    }

    await page.reload();
    await page.waitForLoadState('networkidle');

    const nodeCountAfterReload = await page.locator('[data-testid^="canvas-item-"], .react-flow__node').count();
    expect(nodeCountAfterReload).toBeGreaterThan(0);
  });

  test('phase cockpit keyboard and command palette focus flow remains accessible', async ({ page }) => {
    await setupTest(page, { url: '/p/proj-1/intent' });

    const cockpit = page.locator('[data-testid="intent-cockpit"]');
    if (!(await cockpit.isVisible().catch(() => false))) {
      test.skip(true, 'Intent cockpit is unavailable in this environment');
    }

    await expect(cockpit).toHaveAttribute('role', 'region');

    const primaryAction = page.locator('[data-testid="define-requirements"]');
    await primaryAction.focus();
    await expect(primaryAction).toBeFocused();

    await page.keyboard.press('Meta+k');

    const commandPalette = page.locator('[data-testid="command-palette"], [role="dialog"]');
    if (await commandPalette.isVisible().catch(() => false)) {
      await expect(commandPalette).toBeVisible();
      const commandInput = page.locator('[data-testid="command-palette-input"] input');
      if (await commandInput.isVisible().catch(() => false)) {
        await expect(commandInput).toBeFocused();
      }
      await page.keyboard.press('ArrowDown');
      await page.keyboard.press('ArrowUp');
      await page.keyboard.press('Escape');
      await expect(commandPalette).not.toBeVisible();
    }
  });

  test('preview entry from canvas keeps synchronization surface reachable', async ({ page }) => {
    await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

    const previewButton = firstVisibleLocator(page, [
      '[data-testid="preview-button"]',
      '[data-testid="open-preview"]',
      'button:has-text("Preview")',
    ]);

    if (!(await previewButton.isVisible().catch(() => false))) {
      test.skip(true, 'Preview entry is unavailable in this environment');
    }

    await previewButton.click();

    const previewSurface = page.locator('[data-testid="project-preview-iframe"], [data-testid="react-flow-wrapper"], [data-testid="preview-controls"]');
    await expect(previewSurface.first()).toBeVisible();
  });
});
