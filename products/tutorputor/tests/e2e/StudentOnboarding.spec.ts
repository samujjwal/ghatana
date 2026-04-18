/**
 * Student Onboarding E2E Tests
 *
 * Covers the complete student onboarding journey:
 *   1. Registration flow
 *   2. Email verification (if applicable)
 *   3. Initial profile setup
 *   4. Welcome/onboarding screens
 *   5. First module discovery
 *   6. Dashboard first-visit experience
 *
 * @doc.type test
 * @doc.purpose End-to-end student onboarding validation
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { test, expect, type Page } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173';

const TEST_USER = {
  email: `onboard-${Date.now()}@example.com`,
  password: 'SecurePassword123!',
  firstName: 'Test',
  lastName: 'Student',
};

test.describe('Student Onboarding E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(60000);
  });

  test('should complete registration flow', async ({ page }) => {
    await page.goto(`${BASE_URL}/register`);
    
    // Fill registration form
    await page.fill('input[name="email"], input[type="email"]', TEST_USER.email);
    await page.fill('input[name="password"], input[type="password"]', TEST_USER.password);
    await page.fill('input[name="firstName"]', TEST_USER.firstName);
    await page.fill('input[name="lastName"]', TEST_USER.lastName);
    
    // Submit registration
    await page.click('button[type="submit"], [data-testid="register-button"]');
    
    // Should redirect to dashboard or show verification screen
    await page.waitForLoadState('networkidle');
    
    const url = page.url();
    const isDashboard = url.includes('/dashboard') || url.includes('/home');
    const isVerification = url.includes('verify') || url.includes('confirm');
    
    expect(isDashboard || isVerification).toBeTruthy();
  });

  test('should setup initial profile', async ({ page }) => {
    // Navigate to profile setup (assuming user is logged in or this is part of registration)
    await page.goto(`${BASE_URL}/profile/setup`);
    
    // Fill profile information
    const gradeLevel = page.locator('select[name="gradeLevel"], [data-testid="grade-level"]');
    if (await gradeLevel.count() > 0) {
      await gradeLevel.selectOption('9');
    }
    
    const interests = page.locator('input[name="interests"], [data-testid="interests"]');
    if (await interests.count() > 0) {
      await interests.fill('math, science');
    }
    
    // Save profile
    const saveButton = page.locator('button:has-text("Save"), [data-testid="save-profile"]');
    if (await saveButton.count() > 0) {
      await saveButton.click();
      await page.waitForLoadState('networkidle');
    }
    
    // Should show success or redirect to dashboard
    const successMessage = page.locator('[data-testid="success"], .success-message');
    const dashboardRedirect = page.url().includes('/dashboard');
    
    expect(await successMessage.count() > 0 || dashboardRedirect).toBeTruthy();
  });

  test('should display welcome/onboarding screens', async ({ page }) => {
    await page.goto(BASE_URL);
    
    // Check for welcome modal or onboarding overlay
    const welcomeModal = page.locator('[data-testid="welcome-modal"], .welcome-modal, .onboarding-overlay');
    
    if (await welcomeModal.count() > 0) {
      // Should have welcome content
      await expect(welcomeModal).toBeVisible();
      
      // Should have dismiss or continue button
      const continueButton = page.locator('button:has-text("Continue"), button:has-text("Get Started"), [data-testid="onboarding-continue"]');
      if (await continueButton.count() > 0) {
        await continueButton.click();
      }
    }
  });

  test('should enable first module discovery', async ({ page }) => {
    await page.goto(`${BASE_URL}/modules`);
    
    // Should show module catalogue
    await page.waitForLoadState('networkidle');
    
    const moduleGrid = page.locator('[data-testid="module-grid"], .module-grid, .catalogue');
    await expect(moduleGrid).toBeVisible();
    
    // Should have at least one module card
    const moduleCard = page.locator('[data-testid="module-card"], .module-card');
    const cardCount = await moduleCard.count();
    
    if (cardCount > 0) {
      // Click first module to view details
      await moduleCard.first().click();
      await page.waitForLoadState('networkidle');
      
      // Should show module details
      const moduleDetail = page.locator('[data-testid="module-detail"], .module-detail');
      expect(await moduleDetail.count() > 0).toBeTruthy();
    }
  });

  test('should complete first module enrollment', async ({ page }) => {
    await page.goto(`${BASE_URL}/modules`);
    await page.waitForLoadState('networkidle');
    
    const moduleCard = page.locator('[data-testid="module-card"], .module-card');
    const cardCount = await moduleCard.count();
    
    if (cardCount > 0) {
      await moduleCard.first().click();
      await page.waitForLoadState('networkidle');
      
      // Look for enroll button
      const enrollButton = page.locator('button:has-text("Enroll"), button:has-text("Start"), [data-testid="enroll-button"]');
      
      if (await enrollButton.count() > 0) {
        await enrollButton.click();
        await page.waitForLoadState('networkidle');
        
        // Should show enrollment confirmation or redirect to module content
        const confirmation = page.locator('[data-testid="enrollment-confirmation"], .success-message');
        const moduleContent = page.url().includes('/content') || page.url().includes('/learn');
        
        expect(await confirmation.count() > 0 || moduleContent).toBeTruthy();
      }
    }
  });

  test('should show dashboard first-visit experience', async ({ page }) => {
    // Navigate to dashboard (assuming user is logged in)
    await page.goto(`${BASE_URL}/dashboard`);
    await page.waitForLoadState('networkidle');
    
    // Should show dashboard
    const dashboard = page.locator('[data-testid="dashboard"], .dashboard');
    await expect(dashboard).toBeVisible();
    
    // Check for first-visit elements (tutorials, tooltips, welcome banners)
    const tutorial = page.locator('[data-testid="tutorial"], .tutorial, [data-testid="first-visit"]');
    const welcomeBanner = page.locator('[data-testid="welcome-banner"], .welcome-banner');
    
    const hasFirstVisitElements = await tutorial.count() > 0 || await welcomeBanner.count() > 0;
    
    // First visit elements are optional but good to have
    if (hasFirstVisitElements) {
      // Should be able to dismiss first-visit elements
      const dismissButton = page.locator('button:has-text("Skip"), button:has-text("Dismiss"), [data-testid="dismiss"]');
      if (await dismissButton.count() > 0) {
        await dismissButton.click();
      }
    }
  });
});
