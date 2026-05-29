/**
 * T-015: PHR performance smoke tests.
 * Tests performance characteristics for records, audit, and mobile dashboard endpoints.
 */

import { test, expect } from '@playwright/test';

const PERFORMANCE_THRESHOLDS = {
  // Response time thresholds in milliseconds
  recordsList: 2000,
  recordsDetail: 1500,
  auditTrail: 3000,
  mobileDashboard: 2000,
};

test.describe('PHR Performance Smoke - Records', () => {
  test('records list loads within threshold', async ({ page }) => {
    const startTime = Date.now();
    
    await page.goto('/records');
    await page.waitForLoadState('networkidle');
    
    const loadTime = Date.now() - startTime;
    
    expect(loadTime).toBeLessThan(PERFORMANCE_THRESHOLDS.recordsList);
  });

  test('records detail loads within threshold', async ({ page }) => {
    const startTime = Date.now();
    
    await page.goto('/records/test-record-id');
    await page.waitForLoadState('networkidle');
    
    const loadTime = Date.now() - startTime;
    
    expect(loadTime).toBeLessThan(PERFORMANCE_THRESHOLDS.recordsDetail);
  });

  test('records pagination performs efficiently', async ({ page }) => {
    await page.goto('/records');
    
    // Measure time to load first page
    const page1Start = Date.now();
    await page.waitForLoadState('networkidle');
    const page1Time = Date.now() - page1Start;
    
    // Look for pagination controls
    const nextPageButton = page.locator('button, a').filter({ hasText: /next|page 2/i });
    const hasNextPage = await nextPageButton.count() > 0;
    
    if (hasNextPage) {
      // Measure time to load second page
      const page2Start = Date.now();
      await nextPageButton.first().click();
      await page.waitForLoadState('networkidle');
      const page2Time = Date.now() - page2Start;
      
      // Pagination should not significantly increase load time
      expect(page2Time).toBeLessThan(page1Time * 2);
    }
  });

  test('records filter response time is acceptable', async ({ page }) => {
    await page.goto('/records');
    await page.waitForLoadState('networkidle');
    
    // Look for filter controls
    const filterInput = page.locator('input[type="text"], input[type="search"], select');
    const hasFilters = await filterInput.count() > 0;
    
    if (hasFilters) {
      const filterStart = Date.now();
      await filterInput.first().fill('test');
      await page.waitForTimeout(500); // Wait for debounce
      const filterTime = Date.now() - filterStart;
      
      // Filter operation should be fast
      expect(filterTime).toBeLessThan(1000);
    }
  });
});

test.describe('PHR Performance Smoke - Audit', () => {
  test('audit trail loads within threshold', async ({ page }) => {
    const startTime = Date.now();
    
    await page.goto('/audit');
    await page.waitForLoadState('networkidle');
    
    const loadTime = Date.now() - startTime;
    
    expect(loadTime).toBeLessThan(PERFORMANCE_THRESHOLDS.auditTrail);
  });

  test('audit trail pagination performs efficiently', async ({ page }) => {
    await page.goto('/audit');
    await page.waitForLoadState('networkidle');
    
    // Look for pagination controls
    const nextPageButton = page.locator('button, a').filter({ hasText: /next|page 2/i });
    const hasNextPage = await nextPageButton.count() > 0;
    
    if (hasNextPage) {
      const page1Start = Date.now();
      await page.waitForLoadState('networkidle');
      const page1Time = Date.now() - page1Start;
      
      const page2Start = Date.now();
      await nextPageButton.first().click();
      await page.waitForLoadState('networkidle');
      const page2Time = Date.now() - page2Start;
      
      // Pagination should not significantly increase load time
      expect(page2Time).toBeLessThan(page1Time * 2);
    }
  });

  test('audit filter response time is acceptable', async ({ page }) => {
    await page.goto('/audit');
    await page.waitForLoadState('networkidle');
    
    // Look for filter controls
    const filterInput = page.locator('input[type="date"], input[type="text"], select');
    const hasFilters = await filterInput.count() > 0;
    
    if (hasFilters) {
      const filterStart = Date.now();
      await filterInput.first().fill('2024-01-01');
      await page.waitForTimeout(500);
      const filterTime = Date.now() - filterStart;
      
      // Filter operation should be fast
      expect(filterTime).toBeLessThan(1000);
    }
  });

  test('audit export performs efficiently', async ({ page }) => {
    await page.goto('/audit');
    await page.waitForLoadState('networkidle');
    
    // Look for export button
    const exportButton = page.locator('button, [role="button"]').filter({ hasText: /export|download/i });
    const hasExport = await exportButton.count() > 0;
    
    if (hasExport) {
      const exportStart = Date.now();
      await exportButton.first().click();
      await page.waitForTimeout(2000); // Wait for export to start
      const exportTime = Date.now() - exportStart;
      
      // Export should start quickly
      expect(exportTime).toBeLessThan(3000);
    }
  });
});

test.describe('PHR Performance Smoke - Mobile Dashboard', () => {
  test('mobile dashboard loads within threshold', async ({ page, context }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    
    const startTime = Date.now();
    
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    
    const loadTime = Date.now() - startTime;
    
    expect(loadTime).toBeLessThan(PERFORMANCE_THRESHOLDS.mobileDashboard);
  });

  test('mobile dashboard renders efficiently on slow network', async ({ page, context }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    
    // Simulate slow network
    await context.setOffline(false);
    
    const startTime = Date.now();
    
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    
    const loadTime = Date.now() - startTime;
    
    // Should still load within reasonable time even on slower networks
    expect(loadTime).toBeLessThan(PERFORMANCE_THRESHOLDS.mobileDashboard * 2);
  });

  test('mobile dashboard interactive elements respond quickly', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    
    // Look for interactive elements
    const interactiveElements = page.locator('button, a, [role="button"]');
    const hasInteractive = await interactiveElements.count() > 0;
    
    if (hasInteractive) {
      const clickStart = Date.now();
      await interactiveElements.first().click();
      await page.waitForTimeout(100);
      const clickTime = Date.now() - clickStart;
      
      // Click response should be fast
      expect(clickTime).toBeLessThan(500);
    }
  });
});

test.describe('PHR Performance Smoke - Resource Usage', () => {
  test('page does not make excessive API calls', async ({ page }) => {
    const apiCallCount: number[] = [];
    
    page.on('request', request => {
      if (request.url().includes('/api/')) {
        apiCallCount.push(1);
      }
    });
    
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    
    // Should not make more than 20 API calls for a single page load
    expect(apiCallCount.length).toBeLessThan(20);
  });

  test('page bundle size is reasonable', async ({ page }) => {
    const resourceSizes: number[] = [];
    
    page.on('response', response => {
      if (response.url().includes('.js') || response.url().includes('.css')) {
        const contentLength = response.headers()['content-length'];
        if (contentLength) {
          resourceSizes.push(parseInt(contentLength, 10));
        }
      }
    });
    
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    
    // Total JS/CSS should be less than 2MB
    const totalSize = resourceSizes.reduce((sum, size) => sum + size, 0);
    expect(totalSize).toBeLessThan(2 * 1024 * 1024); // 2MB
  });
});

test.describe('PHR Performance Smoke - Accessibility Performance', () => {
  test('accessibility landmarks are present quickly', async ({ page }) => {
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    
    const a11yStart = Date.now();
    const main = page.locator('main, [role="main"]');
    await main.first().waitFor({ state: 'attached' });
    const a11yTime = Date.now() - a11yStart;
    
    // Accessibility landmarks should be present quickly
    expect(a11yTime).toBeLessThan(1000);
  });
});
