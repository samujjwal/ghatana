/**
 * @fileoverview Studio workflow E2E tests
 *
 * End-to-end tests for Studio workflows including visual builder,
 * design system generation, and navigation.
 *
 * @doc.type test
 * @doc.purpose Studio workflow E2E validation
 * @doc.layer platform
 */

import { test, expect } from '@playwright/test';

test.describe('Studio Workflow E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should navigate to home page', async ({ page }) => {
    await expect(page).toHaveTitle(/Ghatana Studio/);
    await expect(page.locator('h1, h2').first()).toBeVisible();
  });

  test('should navigate to BuilderStudio', async ({ page }) => {
    await page.click('text=Builder Studio');
    await expect(page).toHaveURL(/\/builder-studio/);
    await expect(page.locator('text=ComponentPalette')).toBeVisible();
  });

  test('should create a new builder document', async ({ page }) => {
    await page.click('text=Builder Studio');
    
    // Click create new document button
    const createButton = page.locator('button', { hasText: /create|new/i }).first();
    if (await createButton.isVisible()) {
      await createButton.click();
    }

    // Verify document was created
    await expect(page.locator('text=VisualCanvas').or(page.locator('canvas'))).toBeVisible();
  });

  test('should navigate to Design System Generator', async ({ page }) => {
    await page.click('text=Design System');
    await expect(page).toHaveURL(/\/design-system/);
    await expect(page.locator('text=Design System Generator')).toBeVisible();
  });

  test('should generate CSS variables from preset', async ({ page }) => {
    await page.click('text=Design System');
    
    // Select a preset
    await page.selectOption('select', 'ghatana-default');
    
    // Click generate button
    await page.click('button', { hasText: /generate/i });
    
    // Verify output is displayed
    await expect(page.locator('pre, code')).toBeVisible();
  });

  test('should navigate to Canvas page', async ({ page }) => {
    await page.click('text=Canvas');
    await expect(page).toHaveURL(/\/canvas/);
    await expect(page.locator('text=Artifact Graph Canvas').or(page.locator('canvas'))).toBeVisible();
  });

  test('should navigate between sections', async ({ page }) => {
    const sections = ['Home', 'Blueprints', 'Canvas', 'Develop', 'Design System'];
    
    for (const section of sections) {
      await page.click(`text=${section}`);
      await page.waitForTimeout(500); // Wait for navigation
      await expect(page.locator('h1, h2').first()).toBeVisible();
    }
  });

  test('should display navigation items', async ({ page }) => {
    const navItems = await page.locator('nav a, [role="navigation"] a').all();
    expect(navItems.length).toBeGreaterThan(0);
  });

  test('should handle visual builder component selection', async ({ page }) => {
    await page.click('text=Builder Studio');
    
    // Wait for component palette to load
    await expect(page.locator('text=ComponentPalette').or(page.locator('[data-testid="component-palette"]'))).toBeVisible({ timeout: 5000 });
    
    // Try to select a component (if available)
    const componentButton = page.locator('button', { hasText: /button|card|container/i }).first();
    if (await componentButton.isVisible()) {
      await componentButton.click();
    }
  });

  test('should download generated design system', async ({ page }) => {
    await page.click('text=Design System');
    await page.selectOption('select', 'ghatana-default');
    await page.click('button', { hasText: /generate/i });
    
    // Click download button
    const downloadButton = page.locator('button', { hasText: /download/i }).first();
    if (await downloadButton.isVisible()) {
      const downloadPromise = page.waitForEvent('download');
      await downloadButton.click();
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/\.(css|js|ts|json)$/);
    }
  });
});
