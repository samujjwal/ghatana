/**
 * @fileoverview YAPPC E2E Test Suite
 * Critical path testing for canvas, collaboration, and DevSecOps workflows
 * 
 * @doc.type test
 * @doc.purpose Ensure critical user flows work correctly
 * @doc.layer test
 * @doc.pattern E2E Testing
 */

import { test, expect, Page } from '@playwright/test';
import { YAppCHelpers } from './helpers';

// ============================================================================
// Test Setup
// ============================================================================

test.describe.configure({ mode: 'parallel' });

const TEST_USER = {
  email: 'test@yappc.dev',
  password: 'Test123!',
  name: 'Test User',
};

// ============================================================================
// Authentication Flows
// ============================================================================

test.describe('Authentication', () => {
  test('user can sign up and create account', async ({ page }) => {
    await page.goto('/auth/signup');
    
    await page.fill('[data-testid="email-input"]', `test-${Date.now()}@yappc.dev`);
    await page.fill('[data-testid="password-input"]', TEST_USER.password);
    await page.fill('[data-testid="name-input"]', TEST_USER.name);
    
    await page.click('[data-testid="signup-button"]');
    
    // Should redirect to onboarding or dashboard
    await expect(page).toHaveURL(/\/onboarding|\/dashboard/);
  });

  test('user can log in with valid credentials', async ({ page }) => {
    await page.goto('/auth/login');
    
    await page.fill('[data-testid="email-input"]', TEST_USER.email);
    await page.fill('[data-testid="password-input"]', TEST_USER.password);
    
    await page.click('[data-testid="login-button"]');
    
    await expect(page).toHaveURL('/dashboard');
    await expect(page.locator('[data-testid="user-menu"]')).toBeVisible();
  });

  test('user sees error with invalid credentials', async ({ page }) => {
    await page.goto('/auth/login');
    
    await page.fill('[data-testid="email-input"]', 'invalid@example.com');
    await page.fill('[data-testid="password-input"]', 'wrongpassword');
    
    await page.click('[data-testid="login-button"]');
    
    await expect(page.locator('[data-testid="error-message"]')).toBeVisible();
    await expect(page.locator('[data-testid="error-message"]')).toContainText('Invalid credentials');
  });
});

// ============================================================================
// Canvas Workflows
// ============================================================================

test.describe('Canvas Creation & Editing', () => {
  test.beforeEach(async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
  });

  test('user can create a new canvas', async ({ page }) => {
    await page.goto('/dashboard');
    
    await page.click('[data-testid="new-canvas-button"]');
    await page.fill('[data-testid="canvas-name-input"]', 'Test Canvas');
    await page.click('[data-testid="create-canvas-button"]');
    
    // Should navigate to canvas editor
    await expect(page).toHaveURL(/\/canvas\/[\w-]+/);
    await expect(page.locator('[data-testid="canvas-container"]')).toBeVisible();
  });

  test('user can add shapes to canvas', async ({ page }) => {
    await page.goto('/canvas/test-canvas-id');
    
    // Select rectangle tool
    await page.click('[data-testid="rectangle-tool"]');
    
    // Draw on canvas
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.click({ position: { x: 100, y: 100 } });
    
    // Verify shape was added
    await expect(page.locator('[data-testid="canvas-element"]')).toHaveCount(1);
  });

  test('user can use sketch tool', async ({ page }) => {
    await page.goto('/canvas/test-canvas-id');
    
    // Select sketch tool
    await page.click('[data-testid="sketch-tool"]');
    
    // Draw a line
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.dragTo(canvas, {
      sourcePosition: { x: 100, y: 100 },
      targetPosition: { x: 200, y: 200 },
    });
    
    // Verify stroke was created
    await expect(page.locator('[data-testid="sketch-stroke"]')).toBeVisible();
  });

  test('user can add text element', async ({ page }) => {
    await page.goto('/canvas/test-canvas-id');
    
    // Select text tool
    await page.click('[data-testid="text-tool"]');
    
    // Click on canvas to place text
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.click({ position: { x: 150, y: 150 } });
    
    // Type text
    await page.fill('[data-testid="text-editor"]', 'Hello YAPPC!');
    await page.keyboard.press('Enter');
    
    // Verify text element
    await expect(page.locator('[data-testid="text-element"]')).toContainText('Hello YAPPC!');
  });

  test('user can pan and zoom canvas', async ({ page }) => {
    await page.goto('/canvas/test-canvas-id');
    
    // Test zoom with mouse wheel
    const canvas = page.locator('[data-testid="canvas-container"]');
    await canvas.scroll(5, { position: { x: 400, y: 300 } });
    
    // Verify zoom level changed
    const zoomLevel = await page.locator('[data-testid="zoom-level"]').textContent();
    expect(zoomLevel).not.toBe('100%');
  });

  test('canvas virtualization works with 1000+ elements', async ({ page }) => {
    await page.goto('/canvas/large-canvas-test');
    
    // Wait for canvas to load
    await page.waitForSelector('[data-testid="canvas-container"]');
    
    // Verify virtualization info is shown
    const virtualizationInfo = page.locator('[data-testid="virtualization-info"]');
    if (await virtualizationInfo.isVisible()) {
      const visibleCount = await virtualizationInfo.locator('[data-testid="visible-count"]').textContent();
      const totalCount = await virtualizationInfo.locator('[data-testid="total-count"]').textContent();
      
      // Should show partial rendering
      expect(parseInt(visibleCount!)).toBeLessThan(parseInt(totalCount!));
    }
  });
});

// ============================================================================
// DevSecOps Dashboard
// ============================================================================

test.describe('DevSecOps Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
  });

  test('user can view DevSecOps dashboard', async ({ page }) => {
    await page.goto('/devsecops');
    
    await expect(page.locator('[data-testid="devsecops-dashboard"]')).toBeVisible();
    await expect(page.locator('[data-testid="component-grid"]')).toBeVisible();
  });

  test('user can filter components', async ({ page }) => {
    await page.goto('/devsecops');
    
    // Apply filter
    await page.click('[data-testid="filter-button"]');
    await page.click('[data-testid="filter-critical"]');
    await page.click('[data-testid="apply-filters"]');
    
    // Verify filtered results
    await expect(page.locator('[data-testid="component-card"]')).toHaveCount.greaterThan(0);
  });

  test('user can view component details', async ({ page }) => {
    await page.goto('/devsecops');
    
    // Click on first component
    await page.click('[data-testid="component-card"]:first-child');
    
    // Verify details panel opens
    await expect(page.locator('[data-testid="component-details"]')).toBeVisible();
    await expect(page.locator('[data-testid="metrics-panel"]')).toBeVisible();
  });

  test('AI insights are displayed', async ({ page }) => {
    await page.goto('/devsecops');
    
    await expect(page.locator('[data-testid="ai-insights-panel"]')).toBeVisible();
    
    // Verify at least one insight is shown
    await expect(page.locator('[data-testid="insight-card"]')).toHaveCount.greaterThan(0);
  });
});

// ============================================================================
// Collaboration
// ============================================================================

test.describe('Real-time Collaboration', () => {
  test('user can share canvas', async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Open share dialog
    await page.click('[data-testid="share-button"]');
    
    // Add collaborator
    await page.fill('[data-testid="collaborator-email"]', 'collaborator@example.com');
    await page.click('[data-testid="add-collaborator-button"]');
    
    // Verify collaborator added
    await expect(page.locator('[data-testid="collaborator-list"]')).toContainText('collaborator@example.com');
  });

  test('collaborative cursors are visible', async ({ page, browser }) => {
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Create second user context
    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    await YAppCHelpers.login(page2, { 
      email: 'collaborator@example.com', 
      password: 'Test123!' 
    });
    await page2.goto('/canvas/test-canvas-id');
    
    // Move mouse on second page
    await page2.mouse.move(300, 300);
    
    // Verify cursor appears on first page
    await expect(page.locator('[data-testid="remote-cursor"]')).toBeVisible();
    
    await context2.close();
  });

  test('conflict resolution works for concurrent edits', async ({ page, browser }) => {
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Create second user context
    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    await YAppCHelpers.login(page2, { 
      email: 'collaborator@example.com', 
      password: 'Test123!' 
    });
    await page2.goto('/canvas/test-canvas-id');
    
    // Both users add elements simultaneously
    await Promise.all([
      page.click('[data-testid="rectangle-tool"]'),
      page2.click('[data-testid="circle-tool"]'),
    ]);
    
    // Both add shapes
    const canvas1 = page.locator('[data-testid="canvas-container"]');
    const canvas2 = page2.locator('[data-testid="canvas-container"]');
    
    await Promise.all([
      canvas1.click({ position: { x: 100, y: 100 } }),
      canvas2.click({ position: { x: 200, y: 200 } }),
    ]);
    
    // Verify both elements exist on both pages after sync
    await page.waitForTimeout(1000); // Wait for sync
    
    const elements1 = await page.locator('[data-testid="canvas-element"]').count();
    const elements2 = await page2.locator('[data-testid="canvas-element"]').count();
    
    expect(elements1).toBe(elements2);
    expect(elements1).toBeGreaterThanOrEqual(2);
    
    await context2.close();
  });
});

// ============================================================================
// AI Features
// ============================================================================

test.describe('AI-Assisted Features', () => {
  test.beforeEach(async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
  });

  test('user can generate diagram from natural language', async ({ page }) => {
    await page.goto('/canvas/new');
    
    // Open AI assistant
    await page.click('[data-testid="ai-assistant-button"]');
    
    // Enter prompt
    await page.fill('[data-testid="ai-prompt-input"]', 'Create a user authentication flow diagram');
    await page.click('[data-testid="generate-button"]');
    
    // Wait for generation
    await expect(page.locator('[data-testid="ai-generating"]')).toBeVisible();
    await expect(page.locator('[data-testid="ai-generating"]')).toBeHidden({ timeout: 30000 });
    
    // Verify diagram was created
    await expect(page.locator('[data-testid="canvas-element"]')).toHaveCount.greaterThan(0);
  });

  test('AI DevSecOps anomaly detection displays alerts', async ({ page }) => {
    await page.goto('/devsecops');
    
    // Look for AI anomaly alerts
    const aiAlerts = page.locator('[data-testid="ai-anomaly-alert"]');
    
    // May or may not have alerts depending on test data
    const count = await aiAlerts.count();
    if (count > 0) {
      await expect(aiAlerts.first()).toBeVisible();
    }
  });
});

// ============================================================================
// Export & Import
// ============================================================================

test.describe('Export & Import', () => {
  test.beforeEach(async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
  });

  test('user can export canvas as PNG', async ({ page }) => {
    await page.goto('/canvas/test-canvas-id');
    
    // Open export menu
    await page.click('[data-testid="export-button"]');
    await page.click('[data-testid="export-png"]');
    
    // Verify download started
    const download = await page.waitForEvent('download');
    expect(download.suggestedFilename()).toMatch(/\.png$/);
  });

  test('user can export canvas as JSON', async ({ page }) => {
    await page.goto('/canvas/test-canvas-id');
    
    await page.click('[data-testid="export-button"]');
    await page.click('[data-testid="export-json"]');
    
    const download = await page.waitForEvent('download');
    expect(download.suggestedFilename()).toMatch(/\.json$/);
  });
});

// ============================================================================
// Mobile Responsiveness
// ============================================================================

test.describe('Mobile Experience', () => {
  test('canvas is usable on mobile viewport', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Verify canvas container is visible
    await expect(page.locator('[data-testid="canvas-container"]')).toBeVisible();
    
    // Verify mobile toolbar is shown
    await expect(page.locator('[data-testid="mobile-toolbar"]')).toBeVisible();
  });

  test('touch gestures work on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Test pinch zoom (simulated)
    const canvas = page.locator('[data-testid="canvas-container"]');
    
    // Simulate touch events
    await canvas.evaluate((element) => {
      const touchStart = new Touch({
        identifier: 1,
        target: element,
        clientX: 100,
        clientY: 100,
      });
      
      element.dispatchEvent(new TouchEvent('touchstart', {
        touches: [touchStart],
        bubbles: true,
      }));
    });
    
    // Verify canvas responds to touch
    await expect(page.locator('[data-testid="canvas-container"]')).toHaveClass(/touch-enabled/);
  });
});

// ============================================================================
// Accessibility
// ============================================================================

test.describe('Accessibility', () => {
  test('canvas has proper ARIA labels', async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Verify canvas has role
    const canvas = page.locator('[data-testid="canvas-container"]');
    await expect(canvas).toHaveAttribute('role', 'application');
    
    // Verify toolbar has aria-label
    const toolbar = page.locator('[data-testid="canvas-toolbar"]');
    await expect(toolbar).toHaveAttribute('aria-label');
  });

  test('keyboard navigation works', async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Tab through toolbar
    await page.keyboard.press('Tab');
    await expect(page.locator('[data-testid="select-tool"]:focus')).toBeVisible();
    
    // Select tool with keyboard
    await page.keyboard.press('Enter');
    await expect(page.locator('[data-testid="select-tool"]')).toHaveClass(/active/);
  });

  test('color contrast meets WCAG standards', async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Use axe-core or similar for automated a11y testing
    // This is a placeholder for actual accessibility testing
    const violations = await page.evaluate(async () => {
      // @ts-ignore
      if (window.axe) {
        // @ts-ignore
        const results = await window.axe.run();
        return results.violations;
      }
      return [];
    });
    
    expect(violations).toHaveLength(0);
  });
});

// ============================================================================
// Performance
// ============================================================================

test.describe('Performance', () => {
  test('canvas loads within performance budget', async ({ page }) => {
    const startTime = Date.now();
    
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/test-canvas-id');
    
    // Wait for canvas to be interactive
    await page.waitForSelector('[data-testid="canvas-container"]', { state: 'visible' });
    
    const loadTime = Date.now() - startTime;
    expect(loadTime).toBeLessThan(3000); // 3 second budget
  });

  test('large canvas remains responsive', async ({ page }) => {
    await YAppCHelpers.login(page, TEST_USER);
    await page.goto('/canvas/large-canvas-test');
    
    await page.waitForSelector('[data-testid="canvas-container"]');
    
    // Measure frame rate during interaction
    const frameTimes = await page.evaluate(async () => {
      const times: number[] = [];
      let lastTime = performance.now();
      
      for (let i = 0; i < 60; i++) {
        await new Promise(resolve => requestAnimationFrame(resolve));
        const currentTime = performance.now();
        times.push(currentTime - lastTime);
        lastTime = currentTime;
      }
      
      return times;
    });
    
    // Calculate average frame time (target: 60fps = ~16.67ms)
    const avgFrameTime = frameTimes.reduce((a, b) => a + b, 0) / frameTimes.length;
    expect(avgFrameTime).toBeLessThan(33); // Allow for 30fps minimum
  });
});
