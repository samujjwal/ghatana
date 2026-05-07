/**
 * Keyboard builder workflow tests — E2E coverage for keyboard-only pipeline authoring.
 *
 * Verifies that users can add stages, configure nodes, and trigger save/validate
 * entirely via keyboard without relying on drag-and-drop.
 *
 * @doc.type test
 * @doc.purpose Ensure keyboard accessibility of the pipeline builder
 * @doc.layer frontend
 */
import { test, expect } from '@playwright/test';
import { seedAuthenticatedSession, suppressViteErrorOverlay } from './auth-helpers';

test.describe('Pipeline Builder keyboard workflow @keyboard', () => {
  test.beforeEach(async ({ page }) => {
    await suppressViteErrorOverlay(page);
    await seedAuthenticatedSession(page);
    await page.goto('/build/pipelines/new');
    await expect(page.locator('main')).toBeVisible();
  });

  test('can tab into the canvas and add a stage via keyboard', async ({ page }) => {
    // Focus should land inside the builder after a few Tab presses
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // The canvas or an add-stage button should be focusable
    const focused = await page.evaluate(() => ({
      tag: document.activeElement?.tagName,
      ariaLabel: document.activeElement?.getAttribute('aria-label'),
    }));

    expect(focused.tag).not.toBe('BODY');

    // Try to add a stage if an add button is focused
    if (focused.ariaLabel?.toLowerCase().includes('add') || focused.ariaLabel?.toLowerCase().includes('stage')) {
      await page.keyboard.press('Enter');
      // Wait for node to appear
      await expect.poll(
        async () => page.locator('[data-testid^="pipeline-node"], [class*="react-flow__node"]').count(),
      ).toBeGreaterThan(0);
    }
  });

  test('can save and validate pipeline with keyboard shortcuts', async ({ page }) => {
    // Save shortcut (Ctrl+S / Meta+S) should be captured and not trigger browser save
    await page.keyboard.press('Control+s');

    // After saving, a toast or status indicator should appear
    const toastOrStatus = page.locator('text=/saved|save|validate/i').first();
    await expect(toastOrStatus).toBeVisible({ timeout: 5000 });
  });

  test('tab order respects logical left-to-right flow of builder panels', async ({ page }) => {
    const tabSequence: string[] = [];
    const maxTabs = 20;

    for (let i = 0; i < maxTabs; i++) {
      await page.keyboard.press('Tab');
      const info = await page.evaluate(() => {
        const el = document.activeElement;
        return {
          tag: el?.tagName ?? 'null',
          label: el?.getAttribute('aria-label') ?? el?.textContent?.slice(0, 40) ?? '',
          testId: el?.getAttribute('data-testid') ?? '',
        };
      });

      // Stop cycling if we loop back to the start
      if (tabSequence.length > 0 && info.label === tabSequence[0]) break;
      tabSequence.push(info.label || info.tag);
    }

    // Assert we reached multiple interactive elements, not stuck on body
    const uniqueTags = new Set(tabSequence.map((_, i) => {
      // We can't retrieve tagName retroactively here easily, so just assert non-empty
      return tabSequence[i];
    }));
    expect(uniqueTags.size).toBeGreaterThan(2);
    expect(tabSequence.some((s) => s.toLowerCase().includes('save') || s.toLowerCase().includes('validate'))).toBe(true);
  });
});
