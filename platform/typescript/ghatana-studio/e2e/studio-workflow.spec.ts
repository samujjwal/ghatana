/**
 * @fileoverview Studio workflow E2E tests.
 *
 * End-to-end tests for current Studio navigation, builder, design-system, and
 * canvas routes.
 *
 * @doc.type test
 * @doc.purpose Studio workflow E2E validation
 * @doc.layer platform
 */

import { expect, test } from '@playwright/test';

test.describe('Studio Workflow E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should navigate to home page', async ({ page }) => {
    await expect(page).toHaveTitle(/Ghatana Studio/);
    await expect(page.locator('h1, h2').first()).toBeVisible();
  });

  test('should navigate to Builder route from the sidebar', async ({ page }) => {
    await page.goto('/builder');
    await expect(page).toHaveURL(/\/builder$/);
    await expect(page.getByRole('heading', { name: 'Builder Studio' })).toBeVisible();
  });

  test('should create a new builder document', async ({ page }) => {
    await page.goto('/builder');
    await page.getByRole('button', { name: 'New Document', exact: true }).click();

    await expect(page.getByRole('heading', { name: /New Document/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Components' })).toBeVisible();
  });

  test('should navigate to Design System Generator', async ({ page }) => {
    await page.goto('/design-system');
    await expect(page).toHaveURL(/\/design-system$/);
    await expect(page.getByRole('heading', { name: 'Design System Generator' })).toBeVisible();
  });

  test('should generate CSS variables from preset', async ({ page }) => {
    await page.goto('/design-system');
    await page.getByRole('combobox').first().selectOption('0');
    await page.getByRole('button', { name: 'Generate Design System' }).click();

    await expect(page.getByRole('heading', { name: 'Generated Output' })).toBeVisible();
    await expect(page.locator('pre')).toContainText('--');
  });

  test('should navigate to Canvas page', async ({ page }) => {
    await page.goto('/canvas');
    await expect(page).toHaveURL(/\/canvas$/);
    await expect(page.locator('#canvas-title')).toBeVisible();
    await expect(page.getByText('Artifact Graph Canvas')).toBeVisible();
  });

  test('should navigate between primary sections', async ({ page }) => {
    const sections = [
      { url: '/', path: /\/$/ },
      { url: '/blueprints', path: /\/blueprints$/ },
      { url: '/canvas', path: /\/canvas$/ },
      { url: '/develop', path: /\/develop$/ },
      { url: '/design-system', path: /\/design-system$/ },
    ];

    for (const section of sections) {
      await page.goto(section.url);
      await expect(page).toHaveURL(section.path);
      await expect(page.locator('h1, h2').first()).toBeVisible();
    }
  });

  test('should display navigation items', async ({ page }) => {
    const navItems = await page.locator('nav a, [role="navigation"] a').all();
    expect(navItems.length).toBeGreaterThan(0);
  });

  test('should handle visual builder component selection', async ({ page }) => {
    await page.goto('/builder');
    await page.getByRole('button', { name: 'New Document', exact: true }).click();

    const componentButton = page
      .getByRole('button')
      .filter({ hasText: /button|card|input|container/i })
      .first();
    await expect(componentButton).toBeVisible();
    await componentButton.click();
  });

  test('should download generated design system', async ({ page }) => {
    await page.goto('/design-system');
    await page.getByRole('button', { name: 'Generate Design System' }).click();
    await expect(page.getByRole('heading', { name: 'Generated Output' })).toBeVisible();

    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download' }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.(css|js|tsx|json)$/);
  });
});
