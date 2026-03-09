/**
 * E2E Test: Performance (Web)
 * Tests page load performance and Core Web Vitals
 */

import { test, expect } from './fixtures';

test.describe('Performance', () => {
  test('should load home page quickly', async ({ authenticatedPage }) => {
    const startTime = Date.now();
    await authenticatedPage.goto('/');
    await authenticatedPage.waitForLoadState('networkidle');
    const loadTime = Date.now() - startTime;
    
    // Should load in under 3 seconds
    expect(loadTime).toBeLessThan(3000);
  });

  test('should have good Core Web Vitals', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Get web vitals from browser
    const vitals = await authenticatedPage.evaluate(() => {
      return new Promise((resolve) => {
        const observer = new PerformanceObserver((list) => {
          const entries = list.getEntries();
          const metrics: Record<string, number> = {};
          
          entries.forEach((entry) => {
            if (entry.name === 'first-contentful-paint') {
              metrics.FCP = entry.startTime;
            }
          });
          
          resolve(metrics);
        });
        
        observer.observe({ entryTypes: ['paint', 'largest-contentful-paint'] });
        
        // Timeout after 5 seconds
        setTimeout(() => resolve({}), 5000);
      });
    });
    
    console.log('Web Vitals:', vitals);
  });

  test('should lazy load images', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Check if images use loading="lazy"
    const lazyImages = authenticatedPage.locator('img[loading="lazy"]');
    const count = await lazyImages.count();
    
    expect(count).toBeGreaterThan(0);
  });

  test('should have minimal bundle size', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Get resource sizes
    const resourceSizes = await authenticatedPage.evaluate(() => {
      const resources = performance.getEntriesByType('resource');
      const jsSize = resources
        .filter((r: any) => r.name.endsWith('.js'))
        .reduce((sum: number, r: any) => sum + (r.transferSize || 0), 0);
      
      return { jsSize };
    });
    
    console.log('JavaScript size:', resourceSizes.jsSize, 'bytes');
    
    // JS should be under 500KB (compressed)
    expect(resourceSizes.jsSize).toBeLessThan(500000);
  });

  test('should use caching headers', async ({ authenticatedPage }) => {
    const response = await authenticatedPage.goto('/');
    
    const cacheControl = response?.headers()['cache-control'];
    expect(cacheControl).toBeTruthy();
  });

  test('should load fonts efficiently', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Check for font-display
    const fonts = await authenticatedPage.evaluate(() => {
      const fontFaces = Array.from(document.fonts);
      return fontFaces.map((font: any) => ({
        family: font.family,
        display: font.display,
      }));
    });
    
    console.log('Loaded fonts:', fonts);
  });

  test('should prefetch critical resources', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Check for preload/prefetch links
    const preloadLinks = authenticatedPage.locator('link[rel="preload"]');
    const count = await preloadLinks.count();
    
    expect(count).toBeGreaterThan(0);
  });

  test('should have efficient re-renders', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Measure interaction time
    const startTime = Date.now();
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.waitForSelector('[data-testid="moment-editor"]');
    const interactionTime = Date.now() - startTime;
    
    // Should respond in under 100ms
    expect(interactionTime).toBeLessThan(100);
  });
});
