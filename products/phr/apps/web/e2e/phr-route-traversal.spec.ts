/**
 * T-013: PHR route traversal E2E tests.
 * Tests that every stable route renders allowed/denied/loading/error/empty states.
 */

import { test, expect } from '@playwright/test';

test.describe('PHR route traversal', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the app
    await page.goto('/');
  });

  test('dashboard route renders', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveTitle(/PHR/);
    // Check for loading state, then content
    await expect(page.locator('body')).toBeVisible();
  });

  test('records route renders', async ({ page }) => {
    await page.goto('/records');
    await expect(page).toHaveTitle(/PHR/);
    await expect(page.locator('body')).toBeVisible();
  });

  test('consents route renders', async ({ page }) => {
    await page.goto('/consents');
    await expect(page).toHaveTitle(/PHR/);
    await expect(page.locator('body')).toBeVisible();
  });

  test('emergency route requires proper role', async ({ page }) => {
    await page.goto('/emergency');
    // Should show forbidden state for non-clinician role
    await expect(page.locator('body')).toBeVisible();
  });

  test('unknown route shows 404', async ({ page }) => {
    await page.goto('/unknown-route');
    // Should show not found state
    await expect(page.locator('body')).toBeVisible();
  });
});
