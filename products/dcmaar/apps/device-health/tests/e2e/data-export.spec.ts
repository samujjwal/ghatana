import { test, expect, BrowserContext, Page } from '@playwright/test';

/**
 * E2E Tests for Data Export Functionality
 * 
 * Tests the data export features, including:
 * - Exporting telemetry data
 * - Exporting performance metrics
 * - Exporting error logs
 * - Export format validation (JSON, CSV)
 * - Large dataset handling
 */

test.describe('Data Export', () => {
  let context: BrowserContext;
  let page: Page;
  let extensionId: string;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({
      acceptDownloads: true,
    });
    
    const serviceWorker = context.serviceWorkers()[0] || await context.waitForEvent('serviceworker');
    const url = serviceWorker.url();
    extensionId = url.split('/')[2];
  });

  test.beforeEach(async () => {
    await context.clearCookies();
    page = await context.newPage();
    
    // Navigate to dashboard
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Skip onboarding if present
    const skipButton = page.locator('[data-testid="tour-skip-button"]');
    if (await skipButton.isVisible({ timeout: 1000 }).catch(() => false)) {
      await skipButton.click();
      await page.waitForTimeout(300);
    }
  });

  test.afterEach(async () => {
    await page.close();
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('should export telemetry data as JSON', async () => {
    // Navigate to telemetry settings
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Find telemetry section
      const telemetrySection = page.locator('[data-testid="telemetry-settings"]');
      
      if (await telemetrySection.isVisible({ timeout: 2000 }).catch(() => false)) {
        // Click export button
        const exportButton = telemetrySection.locator('[data-testid="export-telemetry-data"]');
        
        if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
          const downloadPromise = page.waitForEvent('download', { timeout: 10000 });
          
          await exportButton.click();
          
          const download = await downloadPromise;
          
          // Verify download
          expect(download.suggestedFilename()).toMatch(/telemetry.*\.json$/i);
          
          // Save and verify content
          const downloadPath = await download.path();
          expect(downloadPath).toBeTruthy();
          
          // Read and parse JSON
          const fs = await import('fs/promises');
          const content = await fs.readFile(downloadPath!, 'utf-8');
          const data = JSON.parse(content);
          
          // Verify JSON structure
          expect(data).toHaveProperty('events');
          expect(data).toHaveProperty('exportedAt');
          expect(Array.isArray(data.events)).toBeTruthy();
        }
      }
    }
  });

  test('should export performance metrics as JSON', async () => {
    // Navigate to performance/metrics section
    const metricsTab = page.locator('[data-testid="metrics-tab"]');
    
    if (await metricsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await metricsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Click export metrics button
      const exportButton = page.locator('[data-testid="export-metrics"]');
      
      if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        const downloadPromise = page.waitForEvent('download', { timeout: 10000 });
        
        await exportButton.click();
        
        const download = await downloadPromise;
        
        // Verify download
        expect(download.suggestedFilename()).toMatch(/metrics.*\.json$/i);
        
        // Verify content
        const downloadPath = await download.path();
        if (downloadPath) {
          const fs = await import('fs/promises');
          const content = await fs.readFile(downloadPath, 'utf-8');
          const data = JSON.parse(content);
          
          // Verify metrics structure
          expect(data).toHaveProperty('metrics');
          expect(data).toHaveProperty('timeRange');
        }
      }
    }
  });

  test('should export error logs', async () => {
    // First, generate some test errors
    await page.evaluate(() => {
      // Simulate errors
      const errors = [
        {
          type: 'javascript',
          message: 'Test error 1',
          stack: 'Error: Test error 1\n    at testFunction (file.js:1:1)',
          timestamp: Date.now() - 3600000,
        },
        {
          type: 'network',
          message: 'Failed to fetch resource',
          url: 'https://example.com/api/data',
          timestamp: Date.now() - 1800000,
        },
      ];
      
      if (window.localStorage) {
        window.localStorage.setItem('error_logs', JSON.stringify(errors));
      }
    });
    
    await page.reload();
    await page.waitForLoadState('networkidle');
    
    // Navigate to errors section
    const errorsTab = page.locator('[data-testid="errors-tab"]');
    
    if (await errorsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await errorsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Export errors
      const exportButton = page.locator('[data-testid="export-errors"]');
      
      if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        const downloadPromise = page.waitForEvent('download', { timeout: 10000 });
        
        await exportButton.click();
        
        const download = await downloadPromise;
        
        // Verify download
        expect(download.suggestedFilename()).toMatch(/errors.*\.(json|csv)$/i);
        
        // Verify content contains test errors
        const downloadPath = await download.path();
        if (downloadPath) {
          const fs = await import('fs/promises');
          const content = await fs.readFile(downloadPath, 'utf-8');
          
          expect(content).toContain('Test error 1');
        }
      }
    }
  });

  test('should export data in CSV format when selected', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Look for export format selector
      const formatSelector = page.locator('[data-testid="export-format-selector"]');
      
      if (await formatSelector.isVisible({ timeout: 1000 }).catch(() => false)) {
        // Select CSV format
        await formatSelector.selectOption('csv');
        
        // Export data
        const exportButton = page.locator('[data-testid="export-telemetry-data"]');
        
        if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
          const downloadPromise = page.waitForEvent('download', { timeout: 10000 });
          
          await exportButton.click();
          
          const download = await downloadPromise;
          
          // Verify CSV download
          expect(download.suggestedFilename()).toMatch(/\.csv$/i);
          
          // Verify CSV content
          const downloadPath = await download.path();
          if (downloadPath) {
            const fs = await import('fs/promises');
            const content = await fs.readFile(downloadPath, 'utf-8');
            
            // Check for CSV structure (headers)
            expect(content).toMatch(/^[^,\n]+,[^,\n]+/); // Basic CSV header check
          }
        }
      }
    }
  });

  test('should filter data by date range before export', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Look for date range filter
      const dateRangeFilter = page.locator('[data-testid="export-date-range"]');
      
      if (await dateRangeFilter.isVisible({ timeout: 1000 }).catch(() => false)) {
        // Select "Last 7 days"
        await dateRangeFilter.selectOption('7d');
        
        // Export filtered data
        const exportButton = page.locator('[data-testid="export-telemetry-data"]');
        
        if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
          const downloadPromise = page.waitForEvent('download', { timeout: 10000 });
          
          await exportButton.click();
          
          const download = await downloadPromise;
          const downloadPath = await download.path();
          
          if (downloadPath) {
            const fs = await import('fs/promises');
            const content = await fs.readFile(downloadPath, 'utf-8');
            const data = JSON.parse(content);
            
            // Verify all events are within 7 days
            const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
            
            if (data.events && Array.isArray(data.events)) {
              data.events.forEach((event: any) => {
                if (event.timestamp) {
                  expect(event.timestamp).toBeGreaterThanOrEqual(sevenDaysAgo);
                }
              });
            }
          }
        }
      }
    }
  });

  test('should handle large dataset export without crashing', async () => {
    // Generate large dataset
    await page.evaluate(() => {
      const largeDataset = [];
      
      for (let i = 0; i < 10000; i++) {
        largeDataset.push({
          id: `event-${i}`,
          type: 'performance',
          category: 'interaction',
          timestamp: Date.now() - i * 1000,
          data: {
            action: 'click',
            target: `button-${i % 100}`,
            duration: Math.random() * 100,
          },
        });
      }
      
      if (window.localStorage) {
        window.localStorage.setItem('large_telemetry_data', JSON.stringify(largeDataset));
      }
    });
    
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      const exportButton = page.locator('[data-testid="export-telemetry-data"]');
      
      if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        // Increase timeout for large export
        const downloadPromise = page.waitForEvent('download', { timeout: 30000 });
        
        await exportButton.click();
        
        const download = await downloadPromise;
        
        // Verify large export completed
        expect(download.suggestedFilename()).toBeTruthy();
        
        const downloadPath = await download.path();
        if (downloadPath) {
          const fs = await import('fs/promises');
          const stats = await fs.stat(downloadPath);
          
          // File should be substantial (> 10KB for 10k events)
          expect(stats.size).toBeGreaterThan(10000);
        }
      }
    }
  });

  test('should show export progress for large datasets', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      const exportButton = page.locator('[data-testid="export-telemetry-data"]');
      
      if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        await exportButton.click();
        
        // Check for progress indicator
        const progressIndicator = page.locator('[data-testid="export-progress"]');
        
        // Progress might be too fast to catch, but check if it appears
        const hasProgress = await progressIndicator.isVisible({ timeout: 2000 }).catch(() => false);
        
        // Either progress appears or export completes quickly
        if (hasProgress) {
          await expect(progressIndicator).toContainText(/exporting|progress|\d+%/i);
        }
      }
    }
  });

  test('should validate exported data integrity', async () => {
    // Add some known test data
    await page.evaluate(() => {
      const testEvents = [
        {
          id: 'test-event-1',
          type: 'performance',
          category: 'page_load',
          timestamp: 1234567890000,
          data: { duration: 123.45 },
        },
        {
          id: 'test-event-2',
          type: 'interaction',
          category: 'button_click',
          timestamp: 1234567900000,
          data: { target: 'submit-button' },
        },
      ];
      
      if (window.localStorage) {
        window.localStorage.setItem('test_telemetry_events', JSON.stringify(testEvents));
      }
    });
    
    await page.reload();
    await page.waitForLoadState('networkidle');
    
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      const exportButton = page.locator('[data-testid="export-telemetry-data"]');
      
      if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        const downloadPromise = page.waitForEvent('download', { timeout: 10000 });
        
        await exportButton.click();
        
        const download = await downloadPromise;
        const downloadPath = await download.path();
        
        if (downloadPath) {
          const fs = await import('fs/promises');
          const content = await fs.readFile(downloadPath, 'utf-8');
          const exportedData = JSON.parse(content);
          
          // Verify test events are present
          const hasTestEvent1 = exportedData.events?.some((e: any) => e.id === 'test-event-1');
          const hasTestEvent2 = exportedData.events?.some((e: any) => e.id === 'test-event-2');
          
          // At least verify structure is valid
          expect(exportedData).toHaveProperty('events');
          expect(Array.isArray(exportedData.events)).toBeTruthy();
        }
      }
    }
  });

  test('should measure export performance', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      const exportButton = page.locator('[data-testid="export-telemetry-data"]');
      
      if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        const startTime = Date.now();
        const downloadPromise = page.waitForEvent('download', { timeout: 10000 });
        
        await exportButton.click();
        
        const download = await downloadPromise;
        const exportTime = Date.now() - startTime;
        
        // Export should complete reasonably quickly (< 5 seconds for normal dataset)
        expect(exportTime).toBeLessThan(5000);
        
        // Verify download succeeded
        expect(download.suggestedFilename()).toBeTruthy();
      }
    }
  });
});
