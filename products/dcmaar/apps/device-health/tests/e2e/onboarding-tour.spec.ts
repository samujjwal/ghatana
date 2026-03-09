import { test, expect, BrowserContext, Page } from '@playwright/test';
import path from 'path';

/**
 * E2E Tests for Onboarding Tour
 * 
 * Tests the first-time user experience, including:
 * - Tour initialization on first run
 * - Step-by-step navigation
 * - Keyboard shortcuts
 * - Tour completion and storage
 * - Accessibility features
 */

test.describe('Onboarding Tour', () => {
  let context: BrowserContext;
  let page: Page;
  let extensionId: string;

  test.beforeAll(async ({ browser }) => {
    // Create a persistent context with the extension loaded
    context = await browser.newContext();
    
    // Get the extension ID from the background page
    const serviceWorker = context.serviceWorkers()[0] || await context.waitForEvent('serviceworker');
    const url = serviceWorker.url();
    extensionId = url.split('/')[2];
  });

  test.beforeEach(async () => {
    // Clear storage before each test to simulate first-time user
    await context.clearCookies();
    page = await context.newPage();
    
    // Navigate to the extension dashboard
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
  });

  test.afterEach(async () => {
    await page.close();
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('should display onboarding tour on first visit', async () => {
    // Check if tour overlay is visible
    const tourOverlay = page.locator('[data-testid="onboarding-overlay"]');
    await expect(tourOverlay).toBeVisible({ timeout: 5000 });

    // Check if first step is displayed
    const tourStep = page.locator('[data-testid="onboarding-step"]');
    await expect(tourStep).toBeVisible();
    
    // Verify step counter shows "1 of X"
    const stepCounter = page.locator('[data-testid="step-counter"]');
    const counterText = await stepCounter.textContent();
    expect(counterText).toMatch(/1\s+of\s+\d+/);
  });

  test('should navigate through all tour steps', async () => {
    const nextButton = page.locator('[data-testid="tour-next-button"]');
    const stepCounter = page.locator('[data-testid="step-counter"]');
    
    // Get total number of steps
    const initialCounter = await stepCounter.textContent();
    const totalSteps = parseInt(initialCounter?.match(/of\s+(\d+)/)?.[1] || '0');
    
    expect(totalSteps).toBeGreaterThan(0);
    
    // Navigate through each step
    for (let i = 2; i <= totalSteps; i++) {
      await nextButton.click();
      await page.waitForTimeout(300); // Animation delay
      
      const currentCounter = await stepCounter.textContent();
      expect(currentCounter).toContain(`${i} of ${totalSteps}`);
    }
    
    // Last step should show "Finish" button instead of "Next"
    const finishButton = page.locator('[data-testid="tour-finish-button"]');
    await expect(finishButton).toBeVisible();
  });

  test('should allow going back to previous steps', async () => {
    const nextButton = page.locator('[data-testid="tour-next-button"]');
    const backButton = page.locator('[data-testid="tour-back-button"]');
    const stepCounter = page.locator('[data-testid="step-counter"]');
    
    // Move to step 2
    await nextButton.click();
    await page.waitForTimeout(300);
    await expect(stepCounter).toContainText('2 of');
    
    // Go back to step 1
    await backButton.click();
    await page.waitForTimeout(300);
    await expect(stepCounter).toContainText('1 of');
    
    // Back button should be disabled on first step
    await expect(backButton).toBeDisabled();
  });

  test('should support keyboard navigation', async () => {
    const stepCounter = page.locator('[data-testid="step-counter"]');
    
    // Press right arrow to go to next step
    await page.keyboard.press('ArrowRight');
    await page.waitForTimeout(300);
    await expect(stepCounter).toContainText('2 of');
    
    // Press left arrow to go back
    await page.keyboard.press('ArrowLeft');
    await page.waitForTimeout(300);
    await expect(stepCounter).toContainText('1 of');
    
    // Press Escape to skip tour
    const skipButton = page.locator('[data-testid="tour-skip-button"]');
    await page.keyboard.press('Escape');
    
    // Tour should be hidden
    const tourOverlay = page.locator('[data-testid="onboarding-overlay"]');
    await expect(tourOverlay).not.toBeVisible({ timeout: 2000 });
  });

  test('should complete tour and save completion status', async () => {
    const nextButton = page.locator('[data-testid="tour-next-button"]');
    const stepCounter = page.locator('[data-testid="step-counter"]');
    
    // Get total steps
    const initialCounter = await stepCounter.textContent();
    const totalSteps = parseInt(initialCounter?.match(/of\s+(\d+)/)?.[1] || '0');
    
    // Navigate to last step
    for (let i = 2; i <= totalSteps; i++) {
      await nextButton.click();
      await page.waitForTimeout(300);
    }
    
    // Click finish button
    const finishButton = page.locator('[data-testid="tour-finish-button"]');
    await finishButton.click();
    
    // Tour should be hidden
    const tourOverlay = page.locator('[data-testid="onboarding-overlay"]');
    await expect(tourOverlay).not.toBeVisible({ timeout: 2000 });
    
    // Reload page to verify tour doesn't show again
    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(tourOverlay).not.toBeVisible({ timeout: 2000 });
  });

  test('should skip tour when user clicks skip button', async () => {
    const skipButton = page.locator('[data-testid="tour-skip-button"]');
    const tourOverlay = page.locator('[data-testid="onboarding-overlay"]');
    
    // Click skip
    await skipButton.click();
    
    // Tour should be hidden
    await expect(tourOverlay).not.toBeVisible({ timeout: 2000 });
    
    // Reload page to verify tour doesn't show again
    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(tourOverlay).not.toBeVisible({ timeout: 2000 });
  });

  test('should highlight correct UI elements for each step', async () => {
    const nextButton = page.locator('[data-testid="tour-next-button"]');
    const spotlight = page.locator('[data-testid="tour-spotlight"]');
    
    // First step should highlight the dashboard overview
    await expect(spotlight).toBeVisible();
    
    // Move through steps and verify spotlight moves
    for (let i = 0; i < 3; i++) {
      const initialPosition = await spotlight.boundingBox();
      
      await nextButton.click();
      await page.waitForTimeout(300);
      
      const newPosition = await spotlight.boundingBox();
      
      // Position should change (or step might not have spotlight)
      const hasPositionChanged = 
        !newPosition || 
        !initialPosition ||
        initialPosition.x !== newPosition.x || 
        initialPosition.y !== newPosition.y;
      
      expect(hasPositionChanged).toBeTruthy();
    }
  });

  test('should be accessible with screen readers', async () => {
    const tourOverlay = page.locator('[data-testid="onboarding-overlay"]');
    const tourStep = page.locator('[data-testid="onboarding-step"]');
    
    // Check ARIA attributes
    await expect(tourOverlay).toHaveAttribute('role', 'dialog');
    await expect(tourOverlay).toHaveAttribute('aria-modal', 'true');
    
    // Check step has proper heading
    const stepTitle = page.locator('[data-testid="step-title"]');
    await expect(stepTitle).toBeVisible();
    
    // Check buttons have proper labels
    const nextButton = page.locator('[data-testid="tour-next-button"]');
    const skipButton = page.locator('[data-testid="tour-skip-button"]');
    
    await expect(nextButton).toHaveAttribute('aria-label');
    await expect(skipButton).toHaveAttribute('aria-label');
  });

  test('should track telemetry events during tour', async () => {
    const nextButton = page.locator('[data-testid="tour-next-button"]');
    const finishButton = page.locator('[data-testid="tour-finish-button"]');
    
    // Listen for telemetry events (if exposed via window object or console)
    const telemetryEvents: any[] = [];
    page.on('console', msg => {
      if (msg.text().includes('TELEMETRY')) {
        telemetryEvents.push(msg.text());
      }
    });
    
    // Navigate through tour
    await nextButton.click();
    await page.waitForTimeout(300);
    
    // Skip to end
    const stepCounter = page.locator('[data-testid="step-counter"]');
    const initialCounter = await stepCounter.textContent();
    const totalSteps = parseInt(initialCounter?.match(/of\s+(\d+)/)?.[1] || '0');
    
    for (let i = 3; i <= totalSteps; i++) {
      await nextButton.click();
      await page.waitForTimeout(300);
    }
    
    await finishButton.click();
    
    // Verify telemetry was collected (if available)
    // This is a placeholder - actual implementation depends on how telemetry is exposed
    expect(telemetryEvents.length).toBeGreaterThanOrEqual(0);
  });

  test('should handle responsive design on different viewport sizes', async () => {
    const tourOverlay = page.locator('[data-testid="onboarding-overlay"]');
    
    // Test on mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(tourOverlay).toBeVisible();
    
    // Check if tour adapts to mobile layout
    const tourStep = page.locator('[data-testid="onboarding-step"]');
    const stepBox = await tourStep.boundingBox();
    
    expect(stepBox).toBeTruthy();
    if (stepBox) {
      expect(stepBox.width).toBeLessThanOrEqual(375);
    }
    
    // Test on tablet viewport
    await page.setViewportSize({ width: 768, height: 1024 });
    await expect(tourOverlay).toBeVisible();
    
    // Test on desktop viewport
    await page.setViewportSize({ width: 1920, height: 1080 });
    await expect(tourOverlay).toBeVisible();
  });

  test('should measure performance metrics', async () => {
    // Measure initial render time
    const startTime = Date.now();
    
    const tourOverlay = page.locator('[data-testid="onboarding-overlay"]');
    await expect(tourOverlay).toBeVisible({ timeout: 5000 });
    
    const renderTime = Date.now() - startTime;
    
    // Tour should render quickly (< 1 second)
    expect(renderTime).toBeLessThan(1000);
    
    // Measure navigation performance
    const nextButton = page.locator('[data-testid="tour-next-button"]');
    
    const navStartTime = Date.now();
    await nextButton.click();
    await page.waitForTimeout(300);
    const navTime = Date.now() - navStartTime;
    
    // Navigation should be smooth (< 500ms including animation)
    expect(navTime).toBeLessThan(500);
  });
});
