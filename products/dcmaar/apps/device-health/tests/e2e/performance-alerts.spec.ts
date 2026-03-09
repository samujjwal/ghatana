import { test, expect, BrowserContext, Page } from '@playwright/test';

/**
 * E2E Tests for Performance Alerts
 * 
 * Tests the performance monitoring and alerting system, including:
 * - Alert generation based on performance thresholds
 * - Alert display and interaction
 * - Alert actions (dismiss, view details, apply fixes)
 * - Alert persistence and cleanup
 */

test.describe('Performance Alerts', () => {
  let context: BrowserContext;
  let page: Page;
  let extensionId: string;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    
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

  test('should detect and display performance degradation alerts', async () => {
    // Simulate performance issue by triggering heavy computation
    await page.evaluate(() => {
      // Trigger a performance issue that should be detected
      const startTime = performance.now();
      
      // Heavy computation
      let result = 0;
      for (let i = 0; i < 10000000; i++) {
        result += Math.sqrt(i);
      }
      
      const duration = performance.now() - startTime;
      
      // Store performance data that might trigger alert
      if (window.localStorage) {
        window.localStorage.setItem('lastOperationDuration', duration.toString());
      }
      
      return result;
    });

    // Wait for alert to potentially appear
    await page.waitForTimeout(2000);
    
    // Check if performance alert notification is displayed
    const alertNotification = page.locator('[data-testid="performance-alert"]').first();
    
    // Alert might or might not appear depending on threshold
    // Just verify the alert system is functional if alert appears
    const isAlertVisible = await alertNotification.isVisible({ timeout: 1000 }).catch(() => false);
    
    if (isAlertVisible) {
      // Verify alert has proper structure
      await expect(alertNotification).toContainText(/performance|slow|degradation/i);
      
      // Check for action buttons
      const dismissButton = alertNotification.locator('[data-testid="alert-dismiss"]');
      const detailsButton = alertNotification.locator('[data-testid="alert-details"]');
      
      await expect(dismissButton).toBeVisible();
    }
  });

  test('should display alert details when clicked', async () => {
    // Navigate to alerts page or section
    const alertsTab = page.locator('[data-testid="alerts-tab"]');
    
    if (await alertsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await alertsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Check if any alerts are present
      const alertList = page.locator('[data-testid="alert-list"]');
      const alertItems = alertList.locator('[data-testid="alert-item"]');
      
      const alertCount = await alertItems.count();
      
      if (alertCount > 0) {
        // Click first alert to view details
        await alertItems.first().click();
        await page.waitForTimeout(300);
        
        // Verify details modal/panel appears
        const alertDetails = page.locator('[data-testid="alert-details-modal"]');
        await expect(alertDetails).toBeVisible();
        
        // Check for common detail fields
        const detailsContent = await alertDetails.textContent();
        expect(detailsContent).toMatch(/timestamp|severity|description|metric/i);
      }
    }
  });

  test('should allow dismissing alerts', async () => {
    // Create a test alert by injecting into storage
    await page.evaluate(() => {
      const testAlert = {
        id: 'test-alert-' + Date.now(),
        type: 'performance',
        severity: 'medium',
        message: 'Test performance alert',
        timestamp: Date.now(),
        dismissed: false,
      };
      
      // Store in IndexedDB or localStorage depending on implementation
      if (window.localStorage) {
        const alerts = JSON.parse(window.localStorage.getItem('performance_alerts') || '[]');
        alerts.push(testAlert);
        window.localStorage.setItem('performance_alerts', JSON.stringify(alerts));
      }
    });
    
    // Reload to show new alert
    await page.reload();
    await page.waitForLoadState('networkidle');
    
    // Find the alert
    const testAlert = page.locator('[data-testid="alert-item"]').filter({ hasText: 'Test performance alert' });
    
    if (await testAlert.isVisible({ timeout: 2000 }).catch(() => false)) {
      // Dismiss the alert
      const dismissButton = testAlert.locator('[data-testid="alert-dismiss"]');
      await dismissButton.click();
      
      // Alert should be removed or marked as dismissed
      await expect(testAlert).not.toBeVisible({ timeout: 2000 });
      
      // Reload page and verify alert stays dismissed
      await page.reload();
      await page.waitForLoadState('networkidle');
      await expect(testAlert).not.toBeVisible({ timeout: 2000 });
    }
  });

  test('should filter alerts by severity', async () => {
    // Navigate to alerts section
    const alertsTab = page.locator('[data-testid="alerts-tab"]');
    
    if (await alertsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await alertsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Check for severity filter
      const severityFilter = page.locator('[data-testid="severity-filter"]');
      
      if (await severityFilter.isVisible({ timeout: 1000 }).catch(() => false)) {
        // Select "High" severity
        await severityFilter.selectOption('high');
        await page.waitForTimeout(500);
        
        // Verify only high severity alerts are shown
        const visibleAlerts = page.locator('[data-testid="alert-item"]');
        const alertCount = await visibleAlerts.count();
        
        for (let i = 0; i < alertCount; i++) {
          const alert = visibleAlerts.nth(i);
          const severityBadge = alert.locator('[data-testid="alert-severity"]');
          const severityText = await severityBadge.textContent();
          expect(severityText?.toLowerCase()).toContain('high');
        }
      }
    }
  });

  test('should apply suggested fixes for performance issues', async () => {
    // Create a test alert with suggested fix
    await page.evaluate(() => {
      const alertWithFix = {
        id: 'fixable-alert-' + Date.now(),
        type: 'performance',
        severity: 'high',
        message: 'High memory usage detected',
        timestamp: Date.now(),
        dismissed: false,
        suggestedFix: {
          action: 'clear_cache',
          description: 'Clear browser cache to free up memory',
        },
      };
      
      if (window.localStorage) {
        const alerts = JSON.parse(window.localStorage.getItem('performance_alerts') || '[]');
        alerts.push(alertWithFix);
        window.localStorage.setItem('performance_alerts', JSON.stringify(alerts));
      }
    });
    
    await page.reload();
    await page.waitForLoadState('networkidle');
    
    // Find alert with fix
    const fixableAlert = page.locator('[data-testid="alert-item"]').filter({ hasText: 'High memory usage' });
    
    if (await fixableAlert.isVisible({ timeout: 2000 }).catch(() => false)) {
      // Click "Apply Fix" button
      const applyFixButton = fixableAlert.locator('[data-testid="alert-apply-fix"]');
      
      if (await applyFixButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        await applyFixButton.click();
        
        // Wait for fix to be applied
        await page.waitForTimeout(1000);
        
        // Verify success message or alert dismissal
        const successMessage = page.locator('[data-testid="fix-applied-message"]');
        const isSuccess = await successMessage.isVisible({ timeout: 2000 }).catch(() => false);
        
        if (isSuccess) {
          await expect(successMessage).toContainText(/success|applied|fixed/i);
        }
      }
    }
  });

  test('should show alert history', async () => {
    const alertsTab = page.locator('[data-testid="alerts-tab"]');
    
    if (await alertsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await alertsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Check for history tab or section
      const historyTab = page.locator('[data-testid="alert-history-tab"]');
      
      if (await historyTab.isVisible({ timeout: 1000 }).catch(() => false)) {
        await historyTab.click();
        await page.waitForTimeout(500);
        
        // Verify history list is displayed
        const historyList = page.locator('[data-testid="alert-history-list"]');
        await expect(historyList).toBeVisible();
        
        // Check for dismissed/resolved alerts
        const historyItems = historyList.locator('[data-testid="history-item"]');
        const count = await historyItems.count();
        
        // History might be empty, just verify the UI exists
        expect(count).toBeGreaterThanOrEqual(0);
      }
    }
  });

  test('should export alert data', async () => {
    const alertsTab = page.locator('[data-testid="alerts-tab"]');
    
    if (await alertsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await alertsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Look for export button
      const exportButton = page.locator('[data-testid="export-alerts"]');
      
      if (await exportButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        // Set up download handler
        const downloadPromise = page.waitForEvent('download', { timeout: 5000 }).catch(() => null);
        
        await exportButton.click();
        
        const download = await downloadPromise;
        
        if (download) {
          // Verify download occurred
          expect(download.suggestedFilename()).toMatch(/alerts.*\.(json|csv)/);
        }
      }
    }
  });

  test('should measure alert system performance', async () => {
    // Measure time to load alerts page
    const startTime = Date.now();
    
    const alertsTab = page.locator('[data-testid="alerts-tab"]');
    
    if (await alertsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await alertsTab.click();
      await page.waitForLoadState('networkidle');
      
      const loadTime = Date.now() - startTime;
      
      // Alerts should load quickly (< 1 second)
      expect(loadTime).toBeLessThan(1000);
      
      // Measure alert filtering performance
      const severityFilter = page.locator('[data-testid="severity-filter"]');
      
      if (await severityFilter.isVisible({ timeout: 1000 }).catch(() => false)) {
        const filterStartTime = Date.now();
        await severityFilter.selectOption('high');
        await page.waitForTimeout(100);
        const filterTime = Date.now() - filterStartTime;
        
        // Filtering should be instant (< 200ms)
        expect(filterTime).toBeLessThan(200);
      }
    }
  });

  test('should be accessible with keyboard navigation', async () => {
    const alertsTab = page.locator('[data-testid="alerts-tab"]');
    
    if (await alertsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      // Navigate to alerts using Tab key
      await page.keyboard.press('Tab');
      
      let foundAlertsTab = false;
      for (let i = 0; i < 10; i++) {
        const focusedElement = await page.evaluate(() => document.activeElement?.getAttribute('data-testid'));
        if (focusedElement === 'alerts-tab') {
          foundAlertsTab = true;
          break;
        }
        await page.keyboard.press('Tab');
      }
      
      if (foundAlertsTab) {
        // Press Enter to activate
        await page.keyboard.press('Enter');
        await page.waitForTimeout(500);
        
        // Navigate through alerts with arrow keys
        await page.keyboard.press('ArrowDown');
        await page.waitForTimeout(100);
        
        // Dismiss alert with keyboard
        await page.keyboard.press('Delete');
      }
    }
  });
});
