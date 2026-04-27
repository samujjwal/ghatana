/**
 * E2E tests – Guided Onboarding / Feature Discovery
 *
 * Covers: first-time hint badges, feature tooltip show/dismiss,
 * "What can I do here?" command palette entry, localStorage persistence.
 */

import { test, expect } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:7002';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function clearDismissedFeatures(page: Parameters<typeof test>[1] extends { page: infer P } ? P : never) {
  await page.evaluate(() => {
    Object.keys(localStorage).forEach((key) => {
      if (key.startsWith('yappc:dismissed-features') || key.startsWith('yappc:onboarding:')) {
        localStorage.removeItem(key);
      }
    });
  });
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

test.describe('Guided Onboarding / Feature Discovery', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(BASE_URL);
    await clearDismissedFeatures(page);
  });

  // -------------------------------------------------------------------------

  test.skip('shows animated "New" badge on undiscovered features', async ({ page }) => {
    await page.goto(`${BASE_URL}/projects`);
    // Feature badges appear on nav items for new users
    const badge = page.locator('[data-feature="command-palette"] .animate-ping').first();
    await expect(badge).toBeVisible();
  });

  // -------------------------------------------------------------------------

  test.skip('dismissing a feature hint hides the badge', async ({ page }) => {
    await page.goto(`${BASE_URL}/projects`);
    // Trigger the feature discovery tooltip
    const featureTarget = page.locator('[data-feature="command-palette"]').first();
    await featureTarget.click();

    // Find and click "Got it"
    const gotItButton = page.getByRole('button', { name: /Got it/i });
    await expect(gotItButton).toBeVisible();
    await gotItButton.click();

    // Badge should be gone after dismissal
    await expect(page.locator('[data-feature="command-palette"] .animate-ping')).not.toBeVisible();
  });

  // -------------------------------------------------------------------------

  test.skip('dismissed hints persist across page reload', async ({ page }) => {
    await page.goto(`${BASE_URL}/projects`);
    const featureTarget = page.locator('[data-feature="command-palette"]').first();
    await featureTarget.click();

    await page.getByRole('button', { name: /Got it/i }).click();

    // Reload and confirm storage persisted
    await page.reload();
    await expect(page.locator('[data-feature="command-palette"] .animate-ping')).not.toBeVisible();
  });

  // -------------------------------------------------------------------------

  test.skip('"What can I do here?" appears in command palette', async ({ page }) => {
    await page.goto(`${BASE_URL}/projects`);

    // Open command palette (Cmd+K)
    await page.keyboard.press('Meta+k');
    const palette = page.getByRole('dialog');
    await expect(palette).toBeVisible();

    // Search for the action
    await page.keyboard.type('What can I do');
    const item = palette.getByRole('option', { name: /What can I do here/i });
    await expect(item).toBeVisible();
  });

  // -------------------------------------------------------------------------

  test.skip('activating "What can I do here?" opens contextual help', async ({ page }) => {
    await page.goto(`${BASE_URL}/canvas`);

    await page.keyboard.press('Meta+k');
    await page.keyboard.type('What can I do');

    const item = page.getByRole('option', { name: /What can I do here/i });
    await item.click();

    // A contextual help dialog or tooltip should appear
    const helpDialog = page.getByRole('dialog', { name: /feature|help|guide/i }).first();
    await expect(helpDialog).toBeVisible({ timeout: 3000 });
  });

  // -------------------------------------------------------------------------

  test.skip('onboarding checklist shows progress steps', async ({ page }) => {
    await page.goto(`${BASE_URL}/onboarding`);
    const checklist = page.getByTestId('onboarding-checklist');
    await expect(checklist).toBeVisible();
    const steps = checklist.locator('[data-testid^="step-"]');
    await expect(steps).toHaveCount.greaterThan(0);
  });

  // -------------------------------------------------------------------------

  test.skip('completing an onboarding step marks it done', async ({ page }) => {
    await page.goto(`${BASE_URL}/onboarding`);
    const firstStep = page.locator('[data-testid^="step-"]').first();
    const completeBtn = firstStep.getByRole('button', { name: /complete|done|mark/i });
    await completeBtn.click();
    await expect(firstStep.getByRole('img', { name: /check|done/i })).toBeVisible();
  });
});
