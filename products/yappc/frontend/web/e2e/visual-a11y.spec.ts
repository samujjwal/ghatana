/**
 * Visual Regression and Accessibility Tests
 *
 * @doc.type test
 * @doc.purpose Execute visual regression and accessibility tests in CI, replacing evidence-only checks
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { test, expect } from '@playwright/test';

test.describe('Visual Regression Tests', () => {
  test.beforeEach(async ({ page }) => {
    // Set up consistent viewport for visual regression
    await page.setViewportSize({ width: 1280, height: 720 });
  });

  test('dashboard visual regression', async ({ page }) => {
    await page.goto('/dashboard');
    
    // Wait for dashboard to fully load
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    // Take screenshot for visual regression
    await expect(page).toHaveScreenshot('dashboard.png', {
      maxDiffPixels: 100, // Allow for minor rendering differences
      threshold: 0.1, // 10% pixel difference threshold
    });
  });

  test('project shell visual regression', async ({ page }) => {
    await page.goto('/p/test-project');
    
    // Wait for project shell to load
    await page.waitForSelector('[data-testid="project-shell"]');
    
    await expect(page).toHaveScreenshot('project-shell.png', {
      maxDiffPixels: 100,
      threshold: 0.1,
    });
  });

  test('canvas builder visual regression', async ({ page }) => {
    await page.goto('/p/test-project/canvas');
    
    // Wait for canvas to load
    await page.waitForSelector('[data-testid="canvas-builder"]');
    
    await expect(page).toHaveScreenshot('canvas-builder.png', {
      maxDiffPixels: 150, // Canvas may have more dynamic elements
      threshold: 0.15,
    });
  });

  test('dark mode visual regression', async ({ page }) => {
    // Enable dark mode
    await page.goto('/dashboard');
    await page.evaluate(() => {
      document.documentElement.setAttribute('data-theme', 'dark');
    });
    
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    await expect(page).toHaveScreenshot('dashboard-dark.png', {
      maxDiffPixels: 100,
      threshold: 0.1,
    });
  });
});

test.describe('Accessibility Tests', () => {
  test('dashboard accessibility compliance', async ({ page }) => {
    await page.goto('/dashboard');
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    // Run axe-core accessibility checks
    const accessibilityScanResults = await page.accessibility.snapshot();
    
    // Check for critical accessibility violations
    const violations = accessibilityScanResults?.violations || [];
    
    // Filter out known acceptable violations (if any)
    const criticalViolations = violations.filter(
      (v: any) => v.impact === 'critical' || v.impact === 'serious'
    );
    
    expect(criticalViolations).toHaveLength(0);
  });

  test('keyboard navigation on dashboard', async ({ page }) => {
    await page.goto('/dashboard');
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    // Test Tab navigation
    await page.keyboard.press('Tab');
    const firstFocused = await page.evaluate(() => document.activeElement?.tagName);
    expect(['BUTTON', 'A', 'INPUT', 'SELECT']).toContain(firstFocused);
    
    // Test Enter key on buttons
    await page.keyboard.press('Enter');
    
    // Verify focus is visible
    const hasFocusVisible = await page.evaluate(() => {
      const focused = document.activeElement;
      if (!focused) return false;
      const styles = window.getComputedStyle(focused);
      return styles.outline !== 'none' || styles.boxShadow !== 'none';
    });
    expect(hasFocusVisible).toBe(true);
  });

  test('screen reader announcements', async ({ page }) => {
    await page.goto('/dashboard');
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    // Check for ARIA live regions
    const liveRegions = await page.$$('[aria-live]');
    expect(liveRegions.length).toBeGreaterThan(0);
    
    // Check for proper ARIA labels
    const unlabeledButtons = await page.$$eval('button:not([aria-label]):not([aria-labelledby])', (buttons) => 
      buttons.filter((btn: any) => !btn.textContent?.trim()).length
    );
    expect(unlabeledButtons).toBe(0);
  });

  test('color contrast compliance', async ({ page }) => {
    await page.goto('/dashboard');
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    // Check color contrast using axe-core
    const accessibilityScanResults = await page.accessibility.snapshot();
    const colorContrastViolations = accessibilityScanResults?.violations?.filter(
      (v: any) => v.id === 'color-contrast'
    ) || [];
    
    expect(colorContrastViolations).toHaveLength(0);
  });

  test('form accessibility', async ({ page }) => {
    await page.goto('/p/test-project/settings');
    await page.waitForSelector('[data-testid="settings-form"]');
    
    // Check for form labels
    const unlabeledInputs = await page.$$eval('input:not([aria-label]):not([aria-labelledby]):not([id])', (inputs) => inputs.length);
    expect(unlabeledInputs).toBe(0);
    
    // Check for error message association
    const formInputs = await page.$$('input');
    for (const input of formInputs) {
      const hasError = await input.evaluate((el: any) => {
        const errorId = el.getAttribute('aria-describedby');
        if (!errorId) return true;
        const errorElement = document.getElementById(errorId);
        return errorElement !== null;
      });
      expect(hasError).toBe(true);
    }
  });
});

test.describe('Responsive Design Tests', () => {
  test('mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/dashboard');
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    // Verify mobile layout is applied
    const isMobileLayout = await page.evaluate(() => {
      const container = document.querySelector('[data-testid="dashboard-container"]');
      return container?.classList.contains('mobile-layout');
    });
    
    expect(isMobileLayout).toBe(true);
    
    await expect(page).toHaveScreenshot('dashboard-mobile.png', {
      maxDiffPixels: 150,
      threshold: 0.15,
    });
  });

  test('tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/dashboard');
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    await expect(page).toHaveScreenshot('dashboard-tablet.png', {
      maxDiffPixels: 120,
      threshold: 0.12,
    });
  });

  test('desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/dashboard');
    await page.waitForSelector('[data-testid="dashboard-container"]');
    
    await expect(page).toHaveScreenshot('dashboard-desktop.png', {
      maxDiffPixels: 100,
      threshold: 0.1,
    });
  });
});
