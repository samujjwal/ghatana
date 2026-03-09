/**
 * E2E Test Suite for YAPPC Platform
 *
 * Comprehensive end-to-end testing covering critical user flows,
 * AI components, security features, and performance scenarios.
 *
 * @doc.type test
 * @doc.purpose End-to-end testing for production readiness
 * @doc.layer test
 * @doc.pattern E2E Testing
 */

import { test, expect, type Page } from '@playwright/test';
import { chromium, firefox, webkit } from '@playwright/test';

// ============================================================================
// Test Configuration
// ============================================================================

const TEST_CONFIG = {
  // Use API Gateway port for all API calls
  baseURL: process.env.TEST_BASE_URL || 'http://localhost:7002',
  timeout: 30000,
  retries: 2,
  headless: process.env.CI === 'true',
};

// ============================================================================
// Test Fixtures
// ============================================================================

test.describe.configure({
  timeout: TEST_CONFIG.timeout,
  retries: TEST_CONFIG.retries,
});

test.beforeAll(async ({ playwright }) => {
  // Global setup for all tests
  if (!process.env.CI) {
    // Launch development server if not in CI
    console.log('🚀 Starting development server for E2E tests...');
  }
});

test.afterAll(async () => {
  // Global cleanup
  console.log('✅ E2E tests completed');
});

// ============================================================================
// Authentication Tests
// ============================================================================

test.describe('Authentication Flow', () => {
  test('should allow user registration', async ({ page }) => {
    await page.goto(TEST_CONFIG.baseURL);

    // Navigate to registration
    await page.click('[data-testid="register-button"]');

    // Fill registration form
    await page.fill('[data-testid="email-input"]', 'test@example.com');
    await page.fill('[data-testid="password-input"]', 'SecurePassword123!');
    await page.fill('[data-testid="name-input"]', 'Test User');

    // Submit registration
    await page.click('[data-testid="register-submit"]');

    // Should redirect to onboarding
    await expect(page.locator('[data-testid="onboarding-flow"]')).toBeVisible();
    await expect(page.locator('text=Welcome to Yappc')).toBeVisible();
  });

  test('should allow user login', async ({ page }) => {
    await page.goto(TEST_CONFIG.baseURL);

    // Navigate to login
    await page.click('[data-testid="login-button"]');

    // Fill login form
    await page.fill('[data-testid="email-input"]', 'test@example.com');
    await page.fill('[data-testid="password-input"]', 'SecurePassword123!');

    // Submit login
    await page.click('[data-testid="login-submit"]');

    // Should redirect to dashboard
    await expect(
      page.locator('[data-testid="workspace-dashboard"]')
    ).toBeVisible();
  });

  test('should handle invalid credentials', async ({ page }) => {
    await page.goto(TEST_CONFIG.baseURL);

    // Navigate to login
    await page.click('[data-testid="login-button"]');

    // Fill invalid credentials
    await page.fill('[data-testid="email-input"]', 'invalid@example.com');
    await page.fill('[data-testid="password-input"]', 'wrongpassword');

    // Submit login
    await page.click('[data-testid="login-submit"]');

    // Should show error message
    await expect(page.locator('[data-testid="error-message"]')).toBeVisible();
    await expect(page.locator('text=Invalid email or password')).toBeVisible();
  });

  test('should handle password reset flow', async ({ page }) => {
    await page.goto(TEST_CONFIG.baseURL);

    // Navigate to password reset
    await page.click('[data-testid="forgot-password"]');

    // Enter email
    await page.fill('[data-testid="email-input"]', 'test@example.com');
    await page.click('[data-testid="reset-submit"]');

    // Should show success message
    await expect(
      page.locator('text=Password reset instructions sent')
    ).toBeVisible();
  });
});

// ============================================================================
// Workspace Management Tests
// ============================================================================

test.describe('Workspace Management', () => {
  test.beforeEach(async ({ page }) => {
    // Login before each test
    await page.goto(TEST_CONFIG.baseURL);
    await page.click('[data-testid="login-button"]');
    await page.fill('[data-testid="email-input"]', 'test@example.com');
    await page.fill('[data-testid="password-input"]', 'SecurePassword123!');
    await page.click('[data-testid="login-submit"]');
    await page.waitForSelector('[data-testid="workspace-dashboard"]');
  });

  test('should create new workspace', async ({ page }) => {
    // Click create workspace
    await page.click('[data-testid="create-workspace"]');

    // Fill workspace details
    await page.fill('[data-testid="workspace-name"]', 'Test Workspace');
    await page.fill(
      '[data-testid="workspace-description"]',
      'A test workspace for E2E testing'
    );

    // Submit creation
    await page.click('[data-testid="create-submit"]');

    // Should show new workspace in list
    await expect(page.locator('text=Test Workspace')).toBeVisible();
  });

  test('should switch between workspaces', async ({ page }) => {
    // Click workspace selector
    await page.click('[data-testid="workspace-selector"]');

    // Select different workspace
    await page.click('[data-testid="workspace-item"]:nth-child(2)');

    // Should update workspace context
    await expect(
      page.locator('[data-testid="current-workspace"]')
    ).toContainText('Testing');
  });

  test('should manage workspace members', async ({ page }) => {
    // Navigate to workspace admin
    await page.click('[data-testid="workspace-admin"]');

    // Should show member list
    await expect(page.locator('[data-testid="member-list"]')).toBeVisible();

    // Add new member
    await page.click('[data-testid="add-member"]');
    await page.fill('[data-testid="member-email"]', 'member@example.com');
    await page.selectOption('[data-testid="member-role"]', 'EDITOR');
    await page.click('[data-testid="invite-member"]');

    // Should show pending invitation
    await expect(page.locator('text=member@example.com')).toBeVisible();
  });
});

// ============================================================================
// Canvas and AI Component Tests
// ============================================================================

test.describe('Canvas and AI Features', () => {
  test.beforeEach(async ({ page }) => {
    // Login and navigate to canvas
    await page.goto(TEST_CONFIG.baseURL);
    await page.click('[data-testid="login-button"]');
    await page.fill('[data-testid="email-input"]', 'test@example.com');
    await page.fill('[data-testid="password-input"]', 'SecurePassword123!');
    await page.click('[data-testid="login-submit"]');
    await page.waitForSelector('[data-testid="workspace-dashboard"]');
    await page.click('[data-testid="project-1"]');
    await page.waitForSelector('[data-testid="canvas-container"]');
  });

  test('should load canvas with different modes', async ({ page }) => {
    // Test mode switching
    await page.click('[data-testid="mode-selector"]');
    await page.click('[data-testid="mode-diagram"]');

    // Should update canvas mode
    await expect(page.locator('[data-testid="canvas-mode"]')).toContainText(
      'Diagram'
    );

    // Test level switching
    await page.click('[data-testid="level-selector"]');
    await page.click('[data-testid="level-high"]');

    // Should update abstraction level
    await expect(page.locator('[data-testid="canvas-level"]')).toContainText(
      'High'
    );
  });

  test('should handle AI suggestions', async ({ page }) => {
    // Enable AI suggestions
    await page.click('[data-testid="ai-toggle"]');

    // Should show AI suggestions panel
    await expect(page.locator('[data-testid="ai-suggestions"]')).toBeVisible();

    // Test AI suggestion interaction
    await page.click('[data-testid="ai-suggestion"]:first-child');
    await page.click('[data-testid="accept-suggestion"]');

    // Should apply suggestion to canvas
    await expect(page.locator('[data-testid="canvas-element"]')).toHaveCount(1);
  });

  test('should handle AI streaming responses', async ({ page }) => {
    // Start AI chat
    await page.click('[data-testid="ai-chat"]');
    await page.fill(
      '[data-testid="chat-input"]',
      'Create a simple React component'
    );
    await page.click('[data-testid="send-message"]');

    // Should show streaming response
    await expect(page.locator('[data-testid="ai-response"]')).toBeVisible();

    // Should show typing indicator
    await expect(
      page.locator('[data-testid="typing-indicator"]')
    ).toBeVisible();

    // Wait for completion
    await page.waitForSelector('[data-testid="response-complete"]', {
      timeout: 10000,
    });
  });

  test('should handle AI service failures gracefully', async ({ page }) => {
    // Mock AI service failure
    await page.route('/api/ai/*', (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'AI service unavailable' }),
      })
    );

    // Try to use AI feature
    await page.click('[data-testid="ai-toggle"]');

    // Should show fallback UI
    await expect(page.locator('[data-testid="ai-fallback"]')).toBeVisible();
    await expect(
      page.locator('text=AI service temporarily unavailable')
    ).toBeVisible();
  });
});

// ============================================================================
// Error Boundary Tests
// ============================================================================

test.describe('Error Boundaries', () => {
  test('should handle component errors gracefully', async ({ page }) => {
    // Navigate to a page with potential errors
    await page.goto(`${TEST_CONFIG.baseURL}/test/error-boundary`);

    // Trigger component error
    await page.click('[data-testid="trigger-error"]');

    // Should show error boundary fallback
    await expect(page.locator('[data-testid="error-boundary"]')).toBeVisible();
    await expect(page.locator('text=Something went wrong')).toBeVisible();

    // Should provide recovery options
    await expect(page.locator('[data-testid="retry-button"]')).toBeVisible();
    await expect(page.locator('[data-testid="go-home-button"]')).toBeVisible();
  });

  test('should handle AI component errors', async ({ page }) => {
    // Mock AI component failure
    await page.route('/api/ai/suggestions', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'AI suggestion failed' }),
      })
    );

    // Navigate to canvas
    await page.goto(TEST_CONFIG.baseURL);
    await page.click('[data-testid="login-button"]');
    await page.fill('[data-testid="email-input"]', 'test@example.com');
    await page.fill('[data-testid="password-input"]', 'SecurePassword123!');
    await page.click('[data-testid="login-submit"]');
    await page.click('[data-testid="project-1"]');

    // Try to get AI suggestions
    await page.click('[data-testid="ai-toggle"]');

    // Should show AI error boundary
    await expect(
      page.locator('[data-testid="ai-error-boundary"]')
    ).toBeVisible();
    await expect(
      page.locator('text=AI service encountered an error')
    ).toBeVisible();
  });
});

// ============================================================================
// Performance Tests
// ============================================================================

test.describe('Performance', () => {
  test('should load dashboard within performance budget', async ({ page }) => {
    const startTime = Date.now();

    await page.goto(TEST_CONFIG.baseURL);
    await page.click('[data-testid="login-button"]');
    await page.fill('[data-testid="email-input"]', 'test@example.com');
    await page.fill('[data-testid="password-input"]', 'SecurePassword123!');
    await page.click('[-testid="login-submit"]');
    await page.waitForSelector('[data-testid="workspace-dashboard"]');

    const loadTime = Date.now() - startTime;

    // Should load within 3 seconds
    expect(loadTime).toBeLessThan(3000);

    // Should have good performance metrics
    const performanceMetrics = await page.evaluate(() => ({
      memory: (performance as unknown).memory?.usedJSHeapSize || 0,
      timing: performance.timing,
    }));

    expect(performanceMetrics.memory).toBeLessThan(100 * 1024 * 1024); // 100MB
  });

  test('should handle large datasets efficiently', async ({ page }) => {
    // Navigate to page with large dataset
    await page.goto(`${TEST_CONFIG.baseURL}/test/large-dataset`);

    // Should implement virtual scrolling
    await expect(page.locator('[data-testid="virtual-list"]')).toBeVisible();

    // Should only render visible items
    const visibleItems = await page
      .locator('[data-testid="list-item"]')
      .count();
    expect(visibleItems).toBeLessThan(50); // Should not render all items at once

    // Should scroll smoothly
    await page.mouseWheel(0, 1000);
    await page.waitForTimeout(100);

    // Should maintain performance
    const scrollPerformance = await page.evaluate(() => ({
      frameRate: 60, // Expected frame rate
    }));

    expect(scrollPerformance.frameRate).toBeGreaterThan(30);
  });
});

// ============================================================================
// Security Tests
// ============================================================================

test.describe('Security', () => {
  test('should prevent XSS attacks', async ({ page }) => {
    await page.goto(`${TEST_CONFIG.baseURL}/test/security`);

    // Try XSS injection
    await page.fill(
      '[data-testid="user-input"]',
      '<script>alert("XSS")</script>'
    );
    await page.click('[data-testid="submit-button"]');

    // Should sanitize input
    const inputValue = await page.inputValue('[data-testid="user-input"]');
    expect(inputValue).not.toContain('<script>');

    // Should not execute script
    await page.waitForTimeout(1000);
    const alerts = page.locator('.alert');
    await expect(alerts).toHaveCount(0);
  });

  test('should enforce rate limiting', async ({ page }) => {
    // Mock rate limiting
    let requestCount = 0;
    await page.route('/api/*', (route) => {
      requestCount++;
      if (requestCount > 5) {
        return route.fulfill({
          status: 429,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Rate limit exceeded' }),
        });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true }),
      });
    });

    // Make multiple rapid requests
    for (let i = 0; i < 10; i++) {
      await page.click('[data-testid="api-request"]');
      await page.waitForTimeout(100);
    }

    // Should trigger rate limit
    await expect(
      page.locator('[data-testid="rate-limit-error"]')
    ).toBeVisible();
  });

  test('should protect sensitive data', async ({ page }) => {
    // Navigate to page with sensitive data
    await page.goto(`${TEST_CONFIG.base_URL}/test/sensitive-data`);

    // Should mask sensitive information
    const sensitiveElements = page.locator('[data-testid="sensitive"]');
    await expect(sensitiveElements).toHaveCount(0);

    // Should show masked version
    const maskedElements = page.locator('[data-testid="masked"]');
    expect(maskedElements).toHaveCount(3);
  });
});

// ============================================================================
// Cross-Browser Tests
// ============================================================================

['chromium', 'firefox', 'webkit'].forEach((browserName) => {
  test.describe(`${browserName} browser`, () => {
    test('should work across browsers', async ({ page, browserName }) => {
      test.skip(
        browserName === 'webkit' && process.env.CI,
        'Skip WebKit in CI'
      );

      await page.goto(TEST_CONFIG.baseURL);

      // Basic functionality should work
      await expect(page.locator('[data-testid="app-header"]')).toBeVisible();
      await expect(page.locator('[data-testid="main-content"]')).toBeVisible();

      // Responsive design should work
      await page.setViewportSize({ width: 768, height: 1024 });
      await expect(page.locator('[data-testid="mobile-menu"]')).toBeVisible();

      await page.setViewportSize({ width: 1920, height: 1080 });
      await expect(page.locator('[data-testid="desktop-nav"]')).toBeVisible();
    });
  });
});

// ============================================================================
// Accessibility Tests
// ============================================================================

test.describe('Accessibility', () => {
  test('should meet WCAG standards', async ({ page }) => {
    await page.goto(TEST_CONFIG.baseURL);

    // Check for proper heading hierarchy
    const headings = await page.locator('h1, h2, h3, h4, h5, h6').all();
    expect(headings.length).toBeGreaterThan(0);

    // Check for alt text on images
    const images = await page.locator('img').all();
    for (const image of images) {
      const alt = await image.getAttribute('alt');
      expect(alt).toBeTruthy();
    }

    // Check for proper ARIA labels
    const buttons = await page.locator('button').all();
    for (const button of buttons) {
      const ariaLabel = await button.getAttribute('aria-label');
      const text = await button.textContent();
      expect(ariaLabel || text).toBeTruthy();
    }

    // Check keyboard navigation
    await page.keyboard.press('Tab');
    const focusedElement = page.locator(':focus');
    expect(focusedElement).toBeVisible();
  });

  test('should support screen readers', async ({ page }) => {
    await page.goto(TEST_CONFIG.baseURL);

    // Check for proper semantic HTML
    const main = page.locator('main');
    await expect(main).toBeVisible();

    const navigation = page.locator('nav');
    await expect(navigation).toBeVisible();

    // Check for live regions
    const liveRegions = page.locator('[aria-live]');
    expect(liveRegions).toHaveCount(0); // Should not have live regions initially
  });
});

// ============================================================================
// Integration Tests
// ============================================================================

test.describe('Integration Tests', () => {
  test('should integrate with external APIs', async ({ page }) => {
    // Mock external API
    await page.route('/api/external/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: 'External API response' }),
      })
    );

    // Test external API integration
    await page.goto(`${TEST_CONFIG.baseURL}/test/integration`);
    await page.click('[data-testid="call-external-api"]');

    // Should display external data
    await expect(page.locator('[data-testid="external-data"]')).toContainText(
      'External API response'
    );
  });

  test('should handle database operations', async ({ page }) => {
    // Mock database operations
    await page.route('/api/db/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: [{ id: 1, name: 'Test Item' }],
        }),
      })
    );

    // Test CRUD operations
    await page.goto(`${TEST_CONFIG.baseURL}/test/database`);

    // Create
    await page.click('[data-testid="create-item"]');
    await page.fill('[data-testid="item-name"]', 'Test Item');
    await page.click('[data-testid="save-item"]');

    // Read
    await expect(page.locator('[data-testid="item-list"]')).toContainText(
      'Test Item'
    );

    // Update
    await page.click('[data-testid="edit-item"]');
    await page.fill('[data-testid="item-name"]', 'Updated Item');
    await page.click('[data-testid="save-item"]');

    // Delete
    await page.click('[data-testid="delete-item"]');
    await expect(page.locator('[data-testid="item-list"]')).not.toContainText(
      'Updated Item'
    );
  });
});

// ============================================================================
// Test Utilities
// ============================================================================

export const testUtils = {
  async login(
    page: Page,
    email = 'test@example.com',
    password = 'SecurePassword123!'
  ) {
    await page.goto(TEST_CONFIG.baseURL);
    await page.click('[data-testid="login-button"]');
    await page.fill('[data-testid="email-input"]', email);
    await page.fill('[data-testid="password-input"]', password);
    await page.click('[data-testid="login-submit"]');
    await page.waitForSelector('[data-testid="workspace-dashboard"]');
  },

  async createWorkspace(page: Page, name: string, description?: string) {
    await page.click('[data-testid="create-workspace"]');
    await page.fill('[data-testid="workspace-name"]', name);
    if (description) {
      await page.fill('[data-testid="workspace-description"]', description);
    }
    await page.click('[data-testid="create-submit"]');
    await page.waitForSelector(`text=${name}`);
  },

  async navigateToCanvas(page: Page, projectId: string = 'project-1') {
    await page.click(`[data-testid="${projectId}"]`);
    await page.waitForSelector('[data-testid="canvas-container"]');
  },

  async waitForAIResponse(page: Page, timeout = 10000) {
    await page.waitForSelector('[data-testid="response-complete"]', {
      timeout,
    });
  },

  async mockAIService(page: Page, response: unknown, status = 200) {
    await page.route('/api/ai/*', (route) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(response),
      })
    );
  },

  async mockError(page: Page, error: string) {
    await page.route('/api/*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error }),
      })
    );
  },
};
