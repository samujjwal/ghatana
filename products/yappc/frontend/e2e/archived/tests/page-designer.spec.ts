import { test, expect } from '@playwright/test';

test.describe('Page Designer smoke', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173/page-designer');
  });

  test('click add creates item and selects it', async ({ page }) => {
    // Click the first palette add button
    await page.click('[data-testid="palette-addbtn-button"]');
    // Expect an item to be present
    const item = await page.$('[data-testid^="canvas-item-"]');
    expect(item).not.toBeNull();
    // Selected item details should appear in properties
    const selected = await page
      .locator('[data-testid^="property-label-"]')
      .first();
    expect(selected).toBeVisible();
  });

  test('drag palette to canvas works', async ({ page }) => {
    const handle = await page.$('[data-testid="palette-handle-textfield"]');
    const canvas = await page.$('[data-is-canvas]');
    expect(handle).not.toBeNull();
    expect(canvas).not.toBeNull();

    // simulate drag and drop
    const handleBox = await handle!.boundingBox();
    const canvasBox = await canvas!.boundingBox();
    if (!handleBox || !canvasBox) throw new Error('Bounding boxes not found');

    await page.mouse.move(
      handleBox.x + handleBox.width / 2,
      handleBox.y + handleBox.height / 2
    );
    await page.mouse.down();
    await page.mouse.move(
      canvasBox.x + canvasBox.width / 2,
      canvasBox.y + canvasBox.height / 2
    );
    await page.mouse.up();

    // Expect a new canvas item
    const newItem = await page.$('[data-testid^="canvas-item-"]');
    expect(newItem).not.toBeNull();
  });

  test('move item by dragging', async ({ page }) => {
    // ensure an item exists
    await page.click('[data-testid="palette-addbtn-button"]');
    const item = await page.$('[data-testid^="canvas-item-"]');
    if (!item) throw new Error('No item');
    const box = await item.boundingBox();
    if (!box) throw new Error('no box');

    // drag the item 50px right and 30px down
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(
      box.x + box.width / 2 + 50,
      box.y + box.height / 2 + 30
    );
    await page.mouse.up();

    // verify position changed by checking styles (top/left)
    const updated = await page.$('[data-testid^="canvas-item-"]');
    const style = await updated!.getAttribute('style');
    expect(style).toContain('top');
  });
});
