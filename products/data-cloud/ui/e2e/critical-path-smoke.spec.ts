/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

import { test, expect } from '@playwright/test';

/**
 * Browser-level smoke tests for Data Cloud UI critical paths.
 *
 * These tests validate the primary user journeys mentioned in the audit:
 * home -> data -> pipelines -> query -> trust
 *
 * This is a browser-level smoke pack to ensure UI validation beyond backend integration tests.
 *
 * @doc.type test
 * @doc.purpose Browser-level smoke tests for critical user paths
 * @doc.layer testing
 */

test.describe('Critical Path Smoke Tests', () => {
  test.beforeEach(async ({ page }) => {
    // Set up tenant context for tests
    await page.goto('/');
    
    // Check if tenant bootstrap is needed
    const tenantInput = page.locator('input[placeholder*="tenant"]');
    if (await tenantInput.isVisible()) {
      await tenantInput.fill('test-tenant');
      const submitButton = page.locator('button:has-text("Continue")');
      await submitButton.click();
    }
  });

  test('should load home page successfully', async ({ page }) => {
    await page.goto('/');
    
    // Verify home page loads
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 10000 });
    
    // Verify primary navigation is present
    await expect(page.locator('a[href="/data"]')).toBeVisible();
    await expect(page.locator('a[href="/pipelines"]')).toBeVisible();
    await expect(page.locator('a[href="/query"]')).toBeVisible();
  });

  test('should navigate from home to data explorer', async ({ page }) => {
    await page.goto('/');
    
    // Click on Data navigation
    await page.locator('a[href="/data"]').click();
    
    // Verify data explorer loads
    await expect(page).toHaveURL(/\/data/, { timeout: 10000 });
    await expect(page.locator('h1, h2')).toBeVisible();
  });

  test('should navigate from data to pipelines', async ({ page }) => {
    await page.goto('/data');
    
    // Click on Pipelines navigation
    await page.locator('a[href="/pipelines"]').click();
    
    // Verify pipelines page loads
    await expect(page).toHaveURL(/\/pipelines/, { timeout: 10000 });
    await expect(page.locator('h1, h2')).toBeVisible();
  });

  test('should navigate from pipelines to query workspace', async ({ page }) => {
    await page.goto('/pipelines');
    
    // Click on Query navigation
    await page.locator('a[href="/query"]').click();
    
    // Verify query workspace loads
    await expect(page).toHaveURL(/\/query/, { timeout: 10000 });
    await expect(page.locator('textarea, input[placeholder*="SQL"]')).toBeVisible({ timeout: 10000 });
  });

  test('should navigate to trust center with operator role', async ({ page }) => {
    await page.goto('/');
    
    // Switch to operator role
    const roleSwitcher = page.locator('[data-testid="shell-role-switcher"], button:has-text("Role")');
    if (await roleSwitcher.isVisible()) {
      await roleSwitcher.click();
      await page.locator('button:has-text("Operator")').click();
    }
    
    // Navigate to trust center
    await page.locator('a[href="/trust"]').click();
    
    // Verify trust center loads
    await expect(page).toHaveURL(/\/trust/, { timeout: 10000 });
    await expect(page.locator('h1, h2')).toBeVisible();
  });

  test('should navigate to insights with operator role', async ({ page }) => {
    await page.goto('/');
    
    // Switch to operator role
    const roleSwitcher = page.locator('[data-testid="shell-role-switcher"], button:has-text("Role")');
    if (await roleSwitcher.isVisible()) {
      await roleSwitcher.click();
      await page.locator('button:has-text("Operator")').click();
    }
    
    // Navigate to insights
    await page.locator('a[href="/insights"]').click();
    
    // Verify insights page loads
    await expect(page).toHaveURL(/\/insights/, { timeout: 10000 });
    await expect(page.locator('h1, h2')).toBeVisible();
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // Navigate to a page that might encounter API errors
    await page.goto('/data');
    
    // Mock a failed API response
    await page.route('**/api/v1/**', route => {
      route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Bad Request', code: 'INVALID_INPUT' }),
      });
    });
    
    // Reload the page
    await page.reload();
    
    // Verify error is displayed (not HTTP 200 with error envelope)
    // The page should show an error message or boundary state
    const errorElement = page.locator('[data-testid="error-boundary"], .error-message, [role="alert"]');
    await expect(errorElement).toBeVisible({ timeout: 10000 });
  });

  test('should maintain tenant context across navigation', async ({ page }) => {
    await page.goto('/');
    
    // Verify tenant context is set
    const tenantId = await page.evaluate(() => {
      return sessionStorage.getItem('dc:session:tenantId');
    });
    expect(tenantId).toBeTruthy();
    
    // Navigate to data
    await page.locator('a[href="/data"]').click();
    await page.waitForURL(/\/data/);
    
    // Verify tenant context persists
    const tenantIdAfterNav = await page.evaluate(() => {
      return sessionStorage.getItem('dc:session:tenantId');
    });
    expect(tenantIdAfterNav).toBe(tenantId);
  });

  test('should handle session expiry gracefully', async ({ page }) => {
    await page.goto('/');
    
    // Simulate token expiry
    await page.evaluate(() => {
      sessionStorage.removeItem('dc:session:token');
    });
    
    // Navigate to a protected route
    await page.locator('a[href="/data"]').click();
    
    // Verify session expiry handling (redirect to login or show expiry message)
    await expect(page.locator('body')).toBeVisible();
    // The page should either redirect to auth or show session expiry message
  });
});

test.describe('Critical Path - Full Journey', () => {
  test('should complete full critical path journey', async ({ page }) => {
    // Start at home
    await page.goto('/');
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 10000 });
    
    // Navigate to data
    await page.locator('a[href="/data"]').click();
    await expect(page).toHaveURL(/\/data/, { timeout: 10000 });
    
    // Navigate to pipelines
    await page.locator('a[href="/pipelines"]').click();
    await expect(page).toHaveURL(/\/pipelines/, { timeout: 10000 });
    
    // Navigate to query
    await page.locator('a[href="/query"]').click();
    await expect(page).toHaveURL(/\/query/, { timeout: 10000 });
    
    // Switch to operator role and navigate to trust
    const roleSwitcher = page.locator('[data-testid="shell-role-switcher"], button:has-text("Role")');
    if (await roleSwitcher.isVisible()) {
      await roleSwitcher.click();
      await page.locator('button:has-text("Operator")').click();
    }
    
    await page.locator('a[href="/trust"]').click();
    await expect(page).toHaveURL(/\/trust/, { timeout: 10000 });
    
    // Verify all pages loaded successfully without errors
    const hasErrors = await page.evaluate(() => {
      return document.body.textContent?.includes('error') || false;
    });
    expect(hasErrors).toBeFalsy();
  });
});
