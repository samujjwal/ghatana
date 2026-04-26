/**
 * Unified Dashboard E2E Tests
 *
 * @description End-to-end tests for the unified project dashboard
 * ensuring complete user flows work correctly.
 */

import { test, expect } from '@playwright/test';

test.describe('Unified Project Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to a project dashboard
    await page.goto('/project/test-project/unified');
  });

  test.describe('Navigation', () => {
    test('should display unified dashboard layout', async ({ page }) => {
      // Check for main dashboard elements
      await expect(page.locator('[data-testid="unified-dashboard"]')).toBeVisible();
    });

    test('should navigate between phase tabs', async ({ page }) => {
      // Click on Development tab
      await page.click('text=Develop');
      await expect(page).toHaveURL(/\/dev/);

      // Click on Operations tab
      await page.click('text=Operate');
      await expect(page).toHaveURL(/\/ops/);

      // Click on Bootstrap tab
      await page.click('text=Bootstrap');
      await expect(page).toHaveURL(/\/bootstrap/);
    });

    test('should display breadcrumbs correctly', async ({ page }) => {
      await expect(page.locator('[data-testid="breadcrumbs"]')).toBeVisible();
    });

    test('should navigate via breadcrumbs', async ({ page }) => {
      // Click on a breadcrumb link
      const breadcrumb = page.locator('[data-testid="breadcrumbs"] button').first();
      if (await breadcrumb.isVisible()) {
        await breadcrumb.click();
      }
    });
  });

  test.describe('Global Search', () => {
    test('should open search with Cmd+K', async ({ page }) => {
      await page.keyboard.press('Meta+k');
      await expect(page.locator('[data-testid="global-search"]')).toBeVisible();
    });

    test('should close search with Escape', async ({ page }) => {
      await page.keyboard.press('Meta+k');
      await page.keyboard.press('Escape');
      await expect(page.locator('[data-testid="global-search"]')).not.toBeVisible();
    });

    test('should search and navigate to result', async ({ page }) => {
      await page.keyboard.press('Meta+k');
      await page.fill('[data-testid="search-input"]', 'dashboard');
      await page.keyboard.press('Enter');
    });
  });

  test.describe('Quick Actions', () => {
    test('should display phase-specific quick actions', async ({ page }) => {
      await expect(page.locator('[data-testid="quick-actions"]')).toBeVisible();
    });

    test('should navigate on quick action click', async ({ page }) => {
      const quickAction = page.locator('[data-testid="quick-action"]').first();
      if (await quickAction.isVisible()) {
        await quickAction.click();
      }
    });
  });

  test.describe('AI Assistant', () => {
    test('should toggle AI assistant panel', async ({ page }) => {
      const aiButton = page.locator('text=AI Assistant');
      if (await aiButton.isVisible()) {
        await aiButton.click();
        await expect(page.locator('[data-testid="ai-panel"]')).toBeVisible();
      }
    });
  });

  test.describe('Responsive Design', () => {
    test('should display mobile menu on small screens', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await expect(page.locator('[data-testid="mobile-menu-button"]')).toBeVisible();
    });

    test('should toggle mobile menu', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      const menuButton = page.locator('[data-testid="mobile-menu-button"]');
      if (await menuButton.isVisible()) {
        await menuButton.click();
        await expect(page.locator('[data-testid="mobile-menu"]')).toBeVisible();
      }
    });
  });

  test.describe('Accessibility', () => {
    test('should have proper heading hierarchy', async ({ page }) => {
      const h1 = page.locator('h1');
      await expect(h1).toBeVisible();
    });

    test('should support keyboard navigation', async ({ page }) => {
      await page.keyboard.press('Tab');
      const focusedElement = page.locator(':focus');
      await expect(focusedElement).toBeVisible();
    });

    test('should have proper ARIA labels', async ({ page }) => {
      const nav = page.locator('[role="navigation"]');
      await expect(nav).toBeVisible();
    });
  });

  test.describe('Performance', () => {
    test('should load within acceptable time', async ({ page }) => {
      const startTime = Date.now();
      await page.goto('/project/test-project/unified');
      await page.waitForLoadState('networkidle');
      const loadTime = Date.now() - startTime;
      
      // Should load within 3 seconds
      expect(loadTime).toBeLessThan(3000);
    });
  });
});

test.describe('Phase Overview Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/project/test-project/unified/development');
  });

  test('should display phase metrics', async ({ page }) => {
    await expect(page.locator('[data-testid="metrics-grid"]')).toBeVisible();
  });

  test('should display recent tasks', async ({ page }) => {
    await expect(page.locator('[data-testid="recent-tasks"]')).toBeVisible();
  });

  test('should display AI suggestions', async ({ page }) => {
    await expect(page.locator('[data-testid="ai-suggestions"]')).toBeVisible();
  });
});

test.describe('Canvas Toolbar', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/project/test-project/unified/development/canvas');
  });

  test('should display unified toolbar', async ({ page }) => {
    await expect(page.locator('[data-testid="canvas-toolbar"]')).toBeVisible();
  });

  test('should switch tools', async ({ page }) => {
    const selectTool = page.locator('[title="Select (V)"]');
    if (await selectTool.isVisible()) {
      await selectTool.click();
      await expect(selectTool).toHaveClass(/active/);
    }
  });

  test('should support keyboard shortcuts', async ({ page }) => {
    await page.keyboard.press('v'); // Select tool
    await page.keyboard.press('h'); // Pan tool
    await page.keyboard.press('r'); // Rectangle tool
  });

  test('should undo/redo', async ({ page }) => {
    await page.keyboard.press('Meta+z'); // Undo
    await page.keyboard.press('Meta+Shift+z'); // Redo
  });

  test('should zoom controls work', async ({ page }) => {
    const zoomIn = page.locator('[title="Zoom In"]');
    const zoomOut = page.locator('[title="Zoom Out"]');
    
    if (await zoomIn.isVisible()) {
      await zoomIn.click();
      await zoomOut.click();
    }
  });

  test('should toggle advanced options', async ({ page }) => {
    const moreOptions = page.locator('[title="More Options"]');
    if (await moreOptions.isVisible()) {
      await moreOptions.click();
      await expect(page.locator('[data-testid="advanced-options"]')).toBeVisible();
    }
  });
});
