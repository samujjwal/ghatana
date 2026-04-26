import { test, expect } from '@playwright/test';

test('drag palette item to canvas creates item', async ({ page }) => {
  await page.goto('/designer');
  await expect(page.locator('text=Design System Tester')).toBeVisible();

  // Find first draggable palette handle and drag to canvas center
  const handle = page.locator('[data-testid^="palette-handle-"]').first();
  const canvas = page.locator('[data-is-canvas]').first();
  const rect = await canvas.boundingBox();
  if (!rect) return;
  const cx = rect.x + rect.width / 2;
  const cy = rect.y + rect.height / 2;

  await handle.hover();
  await page.mouse.down();
  await page.mouse.move(cx, cy);
  await page.mouse.up();

  // After drop, a canvas item should exist
  const item = page.locator('[data-testid^="canvas-item-"]').first();
  await expect(item).toBeVisible();
});
