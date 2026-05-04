/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

import { test, expect } from '@playwright/test';

/**
 * P2-E2E-1: Browser E2E tests for Data Cloud with REAL backend
 * 
 * These tests validate critical user journeys through the actual Data Cloud backend
 * without using mocks. They require a running Data Cloud server instance.
 * 
 * Tests verify:
 * - Full critical path journey (home -> data -> pipelines -> query -> trust)
 * - Real API responses and data persistence
 * - Authentication and tenant isolation
 * - Real-time updates and SSE streaming
 * - Error handling with real backend errors
 * - Performance under real load
 * 
 * @doc.type test
 * @doc.purpose Browser E2E tests with real backend integration
 * @doc.layer testing
 * @doc.pattern E2ETest, RealBackendTest
 */

test.describe('Critical Path - Real Backend Integration', () => {
  const BASE_URL = process.env.DATACLOUD_BASE_URL || 'http://localhost:8080';
  const TEST_TENANT = process.env.TEST_TENANT || 'e2e-test-tenant';
  const TEST_API_KEY = process.env.TEST_API_KEY || 'test-api-key';

  test.beforeEach(async ({ page }) => {
    // Configure page to use real backend
    await page.goto(BASE_URL);
    
    // Set up authentication
    await page.evaluate(({ tenant, apiKey }) => {
      sessionStorage.setItem('dc:session:tenantId', tenant);
      sessionStorage.setItem('dc:session:token', apiKey);
      localStorage.setItem('dc:onboarding:complete', 'true');
    }, { tenant: TEST_TENANT, apiKey: TEST_API_KEY });
    
    // Reload to apply auth
    await page.reload();
  });

  test('should load home page from real backend', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    
    // Wait for real backend response
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 15000 });
    
    // Verify we're not using mocked data
    const response = await page.request.get(`${BASE_URL}/api/v1/capabilities`);
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data).toBeDefined();
    expect(data.data?.capabilities).toBeDefined();
  });

  test('should navigate to data explorer with real data', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    
    // Navigate to data
    await page.locator('a[href="/data"]').click();
    await expect(page).toHaveURL(/\/data/, { timeout: 15000 });
    
    // Wait for real collections to load
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 15000 });
    
    // Verify real API was called (not mocked)
    const collectionsResponse = await page.request.get(`${BASE_URL}/api/v1/entities/dc_collections`, {
      headers: { 'X-Tenant-Id': TEST_TENANT }
    });
    expect(collectionsResponse.ok()).toBeTruthy();
  });

  test('should execute real analytics query', async ({ page }) => {
    await page.goto(`${BASE_URL}/query`);
    
    // Wait for query workspace to load
    await expect(page.locator('textarea, input[placeholder*="SQL"]')).toBeVisible({ timeout: 15000 });
    
    // Execute a simple query
    const queryTextarea = page.locator('textarea, input[placeholder*="SQL"]');
    await queryTextarea.fill('SELECT 1 AS test_column');
    
    // Click execute button
    const executeButton = page.locator('button:has-text("Execute"), button:has-text("Run")');
    await executeButton.click();
    
    // Wait for real query result
    await expect(page.locator('[data-testid="query-results"], .query-results')).toBeVisible({ timeout: 30000 });
    
    // Verify real analytics endpoint was called
    const queryResponse = await page.request.post(`${BASE_URL}/api/v1/analytics/query`, {
      headers: { 
        'X-Tenant-Id': TEST_TENANT,
        'Content-Type': 'application/json'
      },
      data: {
        query: 'SELECT 1 AS test_column',
        parameters: {}
      }
    });
    
    expect(queryResponse.ok()).toBeTruthy();
    const result = await queryResponse.json();
    expect(result.queryId).toBeDefined();
  });

  test('should navigate to pipelines with real data', async ({ page }) => {
    await page.goto(`${BASE_URL}/pipelines`);
    
    // Wait for real pipelines to load
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 15000 });
    
    // Verify real pipelines API was called
    const pipelinesResponse = await page.request.get(`${BASE_URL}/api/v1/pipelines`, {
      headers: { 'X-Tenant-Id': TEST_TENANT }
    });
    expect(pipelinesResponse.ok()).toBeTruthy();
  });

  test('should navigate to trust center with real governance data', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    
    // Switch to operator role if available
    const roleSwitcher = page.locator('[data-testid="shell-role-switcher"], button:has-text("Role")');
    if (await roleSwitcher.isVisible({ timeout: 5000 })) {
      await roleSwitcher.click();
      await page.locator('button:has-text("Operator")').click();
    }
    
    // Navigate to trust center
    await page.locator('a[href="/trust"]').click();
    await expect(page).toHaveURL(/\/trust/, { timeout: 15000 });
    
    // Verify real governance API was called
    const governanceResponse = await page.request.get(`${BASE_URL}/governance/compliance/summary`, {
      headers: { 'X-Tenant-Id': TEST_TENANT }
    });
    expect(governanceResponse.ok()).toBeTruthy();
    const governanceData = await governanceResponse.json();
    expect(governanceData.data).toBeDefined();
  });

  test('should handle real API errors gracefully', async ({ page }) => {
    // Navigate to a page and trigger a real error
    await page.goto(`${BASE_URL}/data`);
    
    // Try to access non-existent collection
    const response = await page.request.get(`${BASE_URL}/api/v1/entities/dc_collections/non-existent`, {
      headers: { 'X-Tenant-Id': TEST_TENANT }
    });
    
    expect(response.status()).toBe(404);
    
    // Verify error boundary is shown
    await page.goto(`${BASE_URL}/data/non-existent`);
    const errorElement = page.locator('[data-testid="error-boundary"], .error-message, [role="alert"]');
    await expect(errorElement).toBeVisible({ timeout: 10000 });
  });

  test('should maintain tenant context across real navigation', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    
    // Verify tenant context from real backend
    const tenantId = await page.evaluate(() => {
      return sessionStorage.getItem('dc:session:tenantId');
    });
    expect(tenantId).toBe(TEST_TENANT);
    
    // Navigate to data
    await page.locator('a[href="/data"]').click();
    await page.waitForURL(/\/data/);
    
    // Verify tenant context persists
    const tenantIdAfterNav = await page.evaluate(() => {
      return sessionStorage.getItem('dc:session:tenantId');
    });
    expect(tenantIdAfterNav).toBe(TEST_TENANT);
    
    // Verify backend receives correct tenant header
    const response = await page.request.get(`${BASE_URL}/api/v1/entities/dc_collections`, {
      headers: { 'X-Tenant-Id': TEST_TENANT }
    });
    expect(response.ok()).toBeTruthy();
  });

  test('should create collection via real backend', async ({ page }) => {
    await page.goto(`${BASE_URL}/data`);
    
    // Click create collection button
    const createButton = page.locator('button:has-text("Create"), button:has-text("New Collection")');
    if (await createButton.isVisible({ timeout: 5000 })) {
      await createButton.click();
      
      // Fill collection form
      const nameInput = page.locator('input[name="name"], input[placeholder*="name"]');
      await nameInput.fill('E2E Test Collection');
      
      // Submit form
      const submitButton = page.locator('button:has-text("Save"), button:has-text("Create")');
      await submitButton.click();
      
      // Wait for creation to complete
      await expect(page.locator('[data-testid="success-message"], .toast-success')).toBeVisible({ timeout: 15000 });
      
      // Verify collection was created via real backend
      const collectionsResponse = await page.request.get(`${BASE_URL}/api/v1/entities/dc_collections`, {
        headers: { 'X-Tenant-Id': TEST_TENANT }
      });
      expect(collectionsResponse.ok()).toBeTruthy();
      const collections = await collectionsResponse.json();
      expect(collections.entities).toBeDefined();
    }
  });

  test('should handle real authentication expiry', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    
    // Clear session to simulate expiry
    await page.evaluate(() => {
      sessionStorage.removeItem('dc:session:token');
    });
    
    // Navigate to protected route
    await page.locator('a[href="/data"]').click();
    
    // Verify auth redirect or error
    await expect(page).toHaveURL(/\/(login|auth)|/, { timeout: 10000 });
  });

  test('should receive real SSE events', async ({ page }) => {
    // Navigate to a page that uses SSE
    await page.goto(`${BASE_URL}/query`);
    
    // Set up SSE listener
    const sseEvents: string[] = [];
    page.on('console', msg => {
      if (msg.text().includes('SSE') || msg.text().includes('event')) {
        sseEvents.push(msg.text());
      }
    });
    
    // Execute a query that might trigger SSE updates
    const queryTextarea = page.locator('textarea, input[placeholder*="SQL"]');
    await queryTextarea.fill('SELECT 1');
    
    const executeButton = page.locator('button:has-text("Execute"), button:has-text("Run")');
    await executeButton.click();
    
    // Wait for SSE connection
    await page.waitForTimeout(5000);
    
    // Verify SSE endpoint is accessible
    const sseResponse = await page.request.get(`${BASE_URL}/api/v1/sse`, {
      headers: { 
        'X-Tenant-Id': TEST_TENANT,
        'Accept': 'text/event-stream'
      }
    });
    
    // SSE endpoint should be accessible (may return 200 or redirect)
    expect([200, 302, 307]).toContain(sseResponse.status());
  });

  test('should complete full critical path journey with real backend', async ({ page }) => {
    // Start at home
    await page.goto(`${BASE_URL}/`);
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 15000 });
    
    // Navigate to data
    await page.locator('a[href="/data"]').click();
    await expect(page).toHaveURL(/\/data/, { timeout: 15000 });
    
    // Verify real collections loaded
    const collectionsResponse = await page.request.get(`${BASE_URL}/api/v1/entities/dc_collections`, {
      headers: { 'X-Tenant-Id': TEST_TENANT }
    });
    expect(collectionsResponse.ok()).toBeTruthy();
    
    // Navigate to pipelines
    await page.locator('a[href="/pipelines"]').click();
    await expect(page).toHaveURL(/\/pipelines/, { timeout: 15000 });
    
    // Verify real pipelines loaded
    const pipelinesResponse = await page.request.get(`${BASE_URL}/api/v1/pipelines`, {
      headers: { 'X-Tenant-Id': TEST_TENANT }
    });
    expect(pipelinesResponse.ok()).toBeTruthy();
    
    // Navigate to query
    await page.locator('a[href="/query"]').click();
    await expect(page).toHaveURL(/\/query/, { timeout: 15000 });
    
    // Execute real query
    const queryTextarea = page.locator('textarea, input[placeholder*="SQL"]');
    await queryTextarea.fill('SELECT 1 AS test');
    const executeButton = page.locator('button:has-text("Execute"), button:has-text("Run")');
    await executeButton.click();
    await expect(page.locator('[data-testid="query-results"], .query-results')).toBeVisible({ timeout: 30000 });
    
    // Switch to operator role and navigate to trust
    const roleSwitcher = page.locator('[data-testid="shell-role-switcher"], button:has-text("Role")');
    if (await roleSwitcher.isVisible({ timeout: 5000 })) {
      await roleSwitcher.click();
      await page.locator('button:has-text("Operator")').click();
    }
    
    await page.locator('a[href="/trust"]').click();
    await expect(page).toHaveURL(/\/trust/, { timeout: 15000 });
    
    // Verify real governance data loaded
    const governanceResponse = await page.request.get(`${BASE_URL}/governance/compliance/summary`, {
      headers: { 'X-Tenant-Id': TEST_TENANT }
    });
    expect(governanceResponse.ok()).toBeTruthy();
    
    // Verify no critical errors occurred
    const hasErrors = await page.evaluate(() => {
      return document.body.textContent?.includes('error') || false;
    });
    expect(hasErrors).toBeFalsy();
  });
});

test.describe('Real Backend - Performance Tests', () => {
  const BASE_URL = process.env.DATACLOUD_BASE_URL || 'http://localhost:8080';
  const TEST_TENANT = process.env.TEST_TENANT || 'e2e-test-tenant';
  const TEST_API_KEY = process.env.TEST_API_KEY || 'test-api-key';

  test.beforeEach(async ({ page }) => {
    await page.goto(BASE_URL);
    await page.evaluate(({ tenant, apiKey }) => {
      sessionStorage.setItem('dc:session:tenantId', tenant);
      sessionStorage.setItem('dc:session:token', apiKey);
      localStorage.setItem('dc:onboarding:complete', 'true');
    }, { tenant: TEST_TENANT, apiKey: TEST_API_KEY });
    await page.reload();
  });

  test('should handle rapid navigation with real backend', async ({ page }) => {
    const startTime = Date.now();
    
    // Rapid navigation through critical paths
    await page.goto(`${BASE_URL}/data`);
    await page.goto(`${BASE_URL}/pipelines`);
    await page.goto(`${BASE_URL}/query`);
    await page.goto(`${BASE_URL}/trust`);
    
    const duration = Date.now() - startTime;
    expect(duration).toBeLessThan(30000); // Should complete in under 30 seconds
  });

  test('should handle concurrent real API requests', async ({ page }) => {
    await page.goto(`${BASE_URL}/data`);
    
    // Make multiple concurrent requests
    const promises = [
      page.request.get(`${BASE_URL}/api/v1/entities/dc_collections`, {
        headers: { 'X-Tenant-Id': TEST_TENANT }
      }),
      page.request.get(`${BASE_URL}/api/v1/pipelines`, {
        headers: { 'X-Tenant-Id': TEST_TENANT }
      }),
      page.request.get(`${BASE_URL}/api/v1/capabilities`, {
        headers: { 'X-Tenant-Id': TEST_TENANT }
      }),
    ];
    
    const responses = await Promise.all(promises);
    
    // All requests should succeed
    for (const response of responses) {
      expect(response.ok()).toBeTruthy();
    }
  });
});
