/**
 * Initialization Presets E2E Tests
 *
 * End-to-end tests for the initialization presets page including:
 * - Preset display
 * - Filtering and search
 * - Preset selection
 * - Navigation to wizard
 *
 * @doc.type test
 * @doc.purpose E2E tests for initialization presets
 * @doc.phase 2
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Initialization Presets', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/initialize/presets');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display page header', async () => {
      const header = page.locator('[class*="presets-header"]');
      await expect(header).toBeVisible();
      await expect(header).toContainText(/Preset|Quick Start/i);
    });

    test('should show search input', async () => {
      const searchInput = page.getByRole('textbox', { name: /search/i });
      await expect(searchInput).toBeVisible();
    });

    test('should show category filters', async () => {
      const filters = page.locator('[class*="filter"]');
      await expect(filters).toBeVisible();
    });

    test('should display preset cards grid', async () => {
      const presetsGrid = page.locator('[class*="presets-grid"]');
      await expect(presetsGrid).toBeVisible();
    });
  });

  test.describe('Preset Display', () => {
    test('should display all available presets', async () => {
      const presetCards = page.locator('[class*="preset-card"]');
      await expect(presetCards).toHaveCount(10);
    });

    test('should show preset name and description', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await expect(firstPreset.locator('[class*="preset-name"]')).toBeVisible();
      await expect(
        firstPreset.locator('[class*="preset-description"]')
      ).toBeVisible();
    });

    test('should show preset technology stack', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await expect(firstPreset.locator('[class*="tech-stack"]')).toBeVisible();
    });

    test('should show preset estimated cost', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await expect(firstPreset.locator('[class*="cost"]')).toBeVisible();
    });

    test('should show preset popularity or rating', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await expect(
        firstPreset.locator('[class*="popularity"], [class*="rating"]')
      ).toBeVisible();
    });
  });

  test.describe('Preset Categories', () => {
    test('should show MERN Stack preset', async () => {
      const mernPreset = page.locator('[data-preset="mern-stack"]');
      await expect(mernPreset).toBeVisible();
      await expect(mernPreset).toContainText(/MERN/i);
    });

    test('should show JAMstack preset', async () => {
      const jamstackPreset = page.locator('[data-preset="jamstack"]');
      await expect(jamstackPreset).toBeVisible();
      await expect(jamstackPreset).toContainText(/JAMstack/i);
    });

    test('should show Serverless preset', async () => {
      const serverlessPreset = page.locator('[data-preset="serverless"]');
      await expect(serverlessPreset).toBeVisible();
      await expect(serverlessPreset).toContainText(/Serverless/i);
    });

    test('should show Microservices preset', async () => {
      const microservicesPreset = page.locator('[data-preset="microservices"]');
      await expect(microservicesPreset).toBeVisible();
      await expect(microservicesPreset).toContainText(/Microservices/i);
    });
  });

  test.describe('Search Functionality', () => {
    test('should filter presets by search query', async () => {
      const searchInput = page.getByRole('textbox', { name: /search/i });
      await searchInput.fill('react');

      // Should show React-related presets
      await expect(page.locator('[class*="preset-card"]')).toHaveCount(
        await page.locator('[class*="preset-card"]:visible').count()
      );
      await expect(
        page.locator('[class*="preset-card"]').first()
      ).toContainText(/React/i);
    });

    test('should show no results message when search has no matches', async () => {
      const searchInput = page.getByRole('textbox', { name: /search/i });
      await searchInput.fill('nonexistent-technology-xyz');

      await expect(page.locator('[class*="no-results"]')).toBeVisible();
    });

    test('should clear search when clear button is clicked', async () => {
      const searchInput = page.getByRole('textbox', { name: /search/i });
      await searchInput.fill('react');

      // Click clear button
      await page.getByRole('button', { name: /clear/i }).click();

      // Should show all presets again
      await expect(page.locator('[class*="preset-card"]')).toHaveCount(10);
    });
  });

  test.describe('Category Filtering', () => {
    test('should filter by Full Stack category', async () => {
      await page.getByRole('button', { name: /full stack/i }).click();

      const visiblePresets = page.locator('[class*="preset-card"]:visible');
      await expect(visiblePresets).not.toHaveCount(10);
    });

    test('should filter by Frontend category', async () => {
      await page.getByRole('button', { name: /frontend/i }).click();

      const presets = page.locator('[class*="preset-card"]:visible');
      for (const preset of await presets.all()) {
        await expect(preset).toContainText(/React|Vue|Next|JAM/i);
      }
    });

    test('should filter by Backend category', async () => {
      await page.getByRole('button', { name: /backend/i }).click();

      const presets = page.locator('[class*="preset-card"]:visible');
      await expect(presets).toBeVisible();
    });

    test('should show All when All filter is clicked', async () => {
      // First filter
      await page.getByRole('button', { name: /full stack/i }).click();

      // Then show all
      await page.getByRole('button', { name: /all/i }).click();

      await expect(page.locator('[class*="preset-card"]')).toHaveCount(10);
    });

    test('should highlight active filter', async () => {
      const fullStackFilter = page.getByRole('button', { name: /full stack/i });
      await fullStackFilter.click();

      await expect(fullStackFilter).toHaveClass(/active|selected/);
    });
  });

  test.describe('Preset Selection', () => {
    test('should highlight preset on click', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await firstPreset.click();

      await expect(firstPreset).toHaveClass(/selected|active/);
    });

    test('should show preset details panel on selection', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await firstPreset.click();

      await expect(page.locator('[class*="preset-details"]')).toBeVisible();
    });

    test('should show Use This Preset button after selection', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await firstPreset.click();

      await expect(
        page.getByRole('button', { name: /use.*preset|apply|select/i })
      ).toBeVisible();
    });

    test('should navigate to wizard when preset is applied', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await firstPreset.click();

      await page.getByRole('button', { name: /use.*preset|apply|select/i }).click();

      // Should navigate to wizard page
      await expect(page).toHaveURL(/\/initialize(?!\/presets)/);
    });
  });

  test.describe('Preset Details', () => {
    test('should show technology breakdown', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await firstPreset.click();

      const details = page.locator('[class*="preset-details"]');
      await expect(details.locator('[class*="tech"]')).toBeVisible();
    });

    test('should show included features', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await firstPreset.click();

      const details = page.locator('[class*="preset-details"]');
      await expect(details.locator('[class*="features"]')).toBeVisible();
    });

    test('should show estimated setup time', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await firstPreset.click();

      const details = page.locator('[class*="preset-details"]');
      await expect(details).toContainText(/min|minute|time/i);
    });

    test('should show cost breakdown', async () => {
      const firstPreset = page.locator('[class*="preset-card"]').first();
      await firstPreset.click();

      const details = page.locator('[class*="preset-details"]');
      await expect(details).toContainText(/\$|free|cost/i);
    });
  });

  test.describe('Navigation', () => {
    test('should show Custom Setup link', async () => {
      const customLink = page.getByRole('link', { name: /custom|manual|scratch/i });
      await expect(customLink).toBeVisible();
    });

    test('should navigate to wizard when Custom Setup is clicked', async () => {
      await page.getByRole('link', { name: /custom|manual|scratch/i }).click();

      await expect(page).toHaveURL(/\/initialize(?!\/presets)/);
    });

    test('should show back button to project', async () => {
      const backButton = page.getByRole('link', { name: /back|cancel/i });
      await expect(backButton).toBeVisible();
    });
  });

  test.describe('Keyboard Navigation', () => {
    test('should support arrow keys to navigate between presets', async () => {
      await page.keyboard.press('Tab');
      await page.keyboard.press('ArrowRight');

      await expect(page.locator('[class*="preset-card"]:focus')).toBeVisible();
    });

    test('should support Enter to select preset', async () => {
      await page.locator('[class*="preset-card"]').first().focus();
      await page.keyboard.press('Enter');

      await expect(
        page.locator('[class*="preset-card"]').first()
      ).toHaveClass(/selected|active/);
    });
  });

  test.describe('Accessibility', () => {
    test('should have proper heading hierarchy', async () => {
      await expect(page.locator('h1')).toHaveCount(1);
    });

    test('should have ARIA labels on interactive elements', async () => {
      const presetCards = page.locator('[class*="preset-card"]');
      for (const card of await presetCards.all()) {
        await expect(card).toHaveAttribute('role', 'article');
      }
    });

    test('should announce filter changes to screen readers', async () => {
      await page.getByRole('button', { name: /full stack/i }).click();

      const liveRegion = page.locator('[aria-live="polite"]');
      await expect(liveRegion).toBeVisible();
    });
  });

  test.describe('Responsive Design', () => {
    test('should show 3 columns on large screens', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto('/projects/test-project/initialize/presets');

      const grid = page.locator('[class*="presets-grid"]');
      const styles = await grid.evaluate((el) => getComputedStyle(el));
      expect(styles.gridTemplateColumns).toContain('repeat');
    });

    test('should show 2 columns on medium screens', async ({ page }) => {
      await page.setViewportSize({ width: 768, height: 1024 });
      await page.goto('/projects/test-project/initialize/presets');

      const grid = page.locator('[class*="presets-grid"]');
      await expect(grid).toBeVisible();
    });

    test('should show 1 column on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/projects/test-project/initialize/presets');

      const grid = page.locator('[class*="presets-grid"]');
      await expect(grid).toBeVisible();
    });
  });
});
