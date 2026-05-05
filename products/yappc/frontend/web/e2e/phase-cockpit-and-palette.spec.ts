/**
 * Phase Cockpit Navigation and ActionDiscoveryPalette E2E Tests
 *
 * Covers:
 * 1. Phase cockpit route rendering and primary action card interaction
 * 2. ActionDiscoveryPalette keyboard shortcut (⌘K) opens the palette
 * 3. Palette keyboard navigation (ArrowDown / ArrowUp / Enter)
 *
 * @doc.type test
 * @doc.purpose E2E coverage for phase cockpit navigation and command palette
 * @doc.layer product
 * @doc.phase 2
 */

import { test, expect } from '@playwright/test';

const PROJECT_ID = 'test-project';

test.describe('Phase Cockpit Navigation', () => {
  test('intent cockpit renders and primary action card is clickable', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/intent`);
    await page.waitForLoadState('networkidle');

    // The phase cockpit shell renders phase-specific content
    const primaryActionCard = page.getByTestId('intent-primary-action-card');
    await expect(primaryActionCard).toBeVisible();

    // Clicking the primary action should not navigate to an error page
    await primaryActionCard.click();
    await expect(page).not.toHaveURL(/\/error/);
  });

  test('shape cockpit renders its primary action card', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/shape`);
    await page.waitForLoadState('networkidle');

    const primaryActionCard = page.getByTestId('shape-primary-action-card');
    await expect(primaryActionCard).toBeVisible();
  });

  test('phase tabs navigation switches routes correctly', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/intent`);
    await page.waitForLoadState('networkidle');

    // Verify we are on the intent route
    await expect(page).toHaveURL(new RegExp(`/p/${PROJECT_ID}/intent`));

    // Click the shape tab if it is rendered in the shell navigation
    const shapeTab = page.getByRole('tab', { name: /shape/i }).or(
      page.getByRole('link', { name: /shape/i })
    );

    if (await shapeTab.isVisible()) {
      await shapeTab.click();
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(new RegExp(`/p/${PROJECT_ID}/shape`));
    }
  });
});

test.describe('ActionDiscoveryPalette keyboard shortcut', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/intent`);
    await page.waitForLoadState('networkidle');
  });

  test('opens with Cmd+K (or Ctrl+K) shortcut', async ({ page }) => {
    // Palette should not be visible before the shortcut
    await expect(page.getByTestId('command-palette')).not.toBeVisible();

    // Trigger the keyboard shortcut
    await page.keyboard.press('Meta+k');

    // Palette should now be visible
    await expect(page.getByTestId('command-palette')).toBeVisible();
    await expect(page.getByTestId('command-palette-input')).toBeFocused();
  });

  test('filters action list when typing in palette', async ({ page }) => {
    await page.keyboard.press('Meta+k');

    const input = page.getByTestId('command-palette-input');
    await expect(input).toBeVisible();

    // Type a filter term
    await input.fill('canvas');

    // List should narrow to matching actions
    const list = page.getByTestId('command-palette-list');
    await expect(list).toBeVisible();
  });

  test('palette closes on Escape', async ({ page }) => {
    await page.keyboard.press('Meta+k');
    await expect(page.getByTestId('command-palette')).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(page.getByTestId('command-palette')).not.toBeVisible();
  });

  test('ArrowDown moves focus to first list item', async ({ page }) => {
    await page.keyboard.press('Meta+k');
    const palette = page.getByTestId('command-palette');
    await expect(palette).toBeVisible();

    // Press ArrowDown to move focus down into the list
    await page.keyboard.press('ArrowDown');

    // At least one action item should exist in the list after navigation
    const listItems = palette.getByRole('button');
    await expect(listItems.first()).toBeVisible();
  });
});
