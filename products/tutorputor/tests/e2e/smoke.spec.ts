/**
 * Basic E2E Smoke Tests
 * 
 * Critical path smoke tests for TutorPutor applications
 * 
 * @doc.type test
 * @doc.purpose Basic E2E smoke test coverage
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { test, expect } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.BASE_URL || 'http://localhost:5173';
const PLATFORM_URL = process.env.PLATFORM_URL || 'http://localhost:7105';

test.describe('TutorPutor Smoke Tests', () => {
  
  test.beforeEach(async ({ page }) => {
    // Set default timeouts
    test.setTimeout(30000);
  });

  test.describe('Web Application', () => {
    
    test('should load main dashboard', async ({ page }) => {
      await page.goto(BASE_URL);
      
      // Check page loads without errors
      await expect(page).toHaveTitle(/TutorPutor/);
      
      // Check main navigation elements
      await expect(page.locator('nav')).toBeVisible();
      
      // Check no critical JavaScript errors
      const errors: string[] = [];
      page.on('pageerror', error => errors.push(error.message));
      
      await page.waitForLoadState('networkidle');
      expect(errors).toHaveLength(0);
    });

    test('should navigate to AI Tutor page', async ({ page }) => {
      await page.goto(BASE_URL);
      
      // Navigate to AI Tutor
      await page.click('[data-testid="nav-ai-tutor"], a[href*="ai-tutor"]');
      await page.waitForLoadState('networkidle');
      
      // Verify AI Tutor interface loads
      await expect(page.locator('h1, h2')).toContainText(/AI Tutor|Intelligent Tutor/);
    });

    test('should load dashboard with metrics', async ({ page }) => {
      await page.goto(`${BASE_URL}/dashboard`);
      
      // Wait for dashboard to load
      await page.waitForLoadState('networkidle');
      
      // Check dashboard elements
      await expect(page.locator('[data-testid="dashboard-container"], .dashboard')).toBeVisible();
      
      // Verify metrics display (if available)
      const metricsElements = page.locator('[data-testid*="metric"], .stat, .metric');
      if (await metricsElements.count() > 0) {
        await expect(metricsElements.first()).toBeVisible();
      }
    });

    test('should handle authentication flow', async ({ page }) => {
      await page.goto(BASE_URL);
      
      // Look for login/signup buttons
      const loginButton = page.locator('[data-testid="login"], button:has-text("Login"), a:has-text("Sign")');
      
      if (await loginButton.count() > 0) {
        await loginButton.first().click();
        await page.waitForLoadState('networkidle');
        
        // Should see login form or redirect to auth
        const loginForm = page.locator('form');
        const authRedirect = page.url().includes('auth') || page.url().includes('login');
        
        expect(await loginForm.count() > 0 || authRedirect).toBeTruthy();
      }
    });
  });

  test.describe('Platform Service', () => {
    
    test('should respond to health check', async ({ request }) => {
      const response = await request.get(`${PLATFORM_URL}/health`);
      
      // Health endpoint should be accessible
      expect(response.status()).toBe(200);
      
      const body = await response.json();
      expect(body).toHaveProperty('status');
    });

    test('should serve API documentation', async ({ page }) => {
      await page.goto(`${PLATFORM_URL}/docs`);
      
      // Check if docs load (Swagger/OpenAPI)
      await page.waitForLoadState('networkidle');
      
      // Look for Swagger UI elements or API documentation
      const swaggerUI = page.locator('.swagger-ui, [data-testid="swagger"], #swagger-ui');
      const apiDocs = page.locator('h1:has-text("API"), h2:has-text("Documentation")');
      
      expect(await swaggerUI.count() > 0 || await apiDocs.count() > 0).toBeTruthy();
    });

    test('should handle CORS properly', async ({ request }) => {
      const response = await request.get(`${PLATFORM_URL}/api/test`, {
        headers: { 'Origin': BASE_URL }
      });
      
      // Should have proper CORS headers
      const corsHeader = response.headers()['access-control-allow-origin'];
      expect(corsHeader).toBeTruthy();
    });
  });

  test.describe('Error Handling', () => {
    
    test('should handle 404 pages gracefully', async ({ page }) => {
      await page.goto(`${BASE_URL}/non-existent-page`);
      
      // Should show proper 404 page or redirect
      const notFoundElement = page.locator('[data-testid="404"], h1:has-text("404"), h1:has-text("Not Found")');
      const redirectHome = page.url().endsWith('/') || page.url().endsWith('/dashboard');
      
      expect(await notFoundElement.count() > 0 || redirectHome).toBeTruthy();
    });

    test('should handle network failures gracefully', async ({ page }) => {
      // Simulate network failure
      await page.route('**/api/**', route => route.abort());
      
      await page.goto(BASE_URL);
      await page.waitForLoadState('networkidle');
      
      // Should show error state or handle gracefully
      const errorElement = page.locator('[data-testid="error"], .error-message, [data-testid="network-error"]');
      const stillFunctional = page.locator('nav, header, main').isVisible();
      
      expect(await errorElement.count() > 0 || stillFunctional).toBeTruthy();
    });
  });

  test.describe('Performance', () => {
    
    test('should load within reasonable time', async ({ page }) => {
      const startTime = Date.now();
      
      await page.goto(BASE_URL);
      await page.waitForLoadState('networkidle');
      
      const loadTime = Date.now() - startTime;
      
      // Should load within 5 seconds
      expect(loadTime).toBeLessThan(5000);
    });

    test('should have responsive design', async ({ page }) => {
      await page.goto(BASE_URL);
      
      // Test mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });
      await page.waitForLoadState('networkidle');
      
      // Should be usable on mobile
      const mobileNav = page.locator('[data-testid="mobile-nav"], .mobile-menu, nav');
      await expect(mobileNav).toBeVisible();
      
      // Test desktop viewport
      await page.setViewportSize({ width: 1920, height: 1080 });
      await page.waitForLoadState('networkidle');
      
      // Should adapt to desktop
      const desktopContent = page.locator('main, .container, .content');
      await expect(desktopContent).toBeVisible();
    });
  });
});

// Helper functions for test utilities
export class TestUtils {
  static async login(page: any, email: string, password: string) {
    await page.goto(`${BASE_URL}/login`);
    await page.fill('[data-testid="email"], input[name="email"], input[type="email"]', email);
    await page.fill('[data-testid="password"], input[name="password"], input[type="password"]', password);
    await page.click('[data-testid="login-button"], button[type="submit"]');
    await page.waitForLoadState('networkidle');
  }

  static async waitForAPIResponse(page: any, endpoint: string) {
    return page.waitForResponse(response => 
      response.url().includes(endpoint) && response.status() === 200
    );
  }

  static async checkAccessibility(page: any) {
    // Basic accessibility checks
    const headings = await page.locator('h1, h2, h3, h4, h5, h6').count();
    const buttons = await page.locator('button, [role="button"]').count();
    const inputs = await page.locator('input, select, textarea').count();
    
    return { headings, buttons, inputs };
  }
}
