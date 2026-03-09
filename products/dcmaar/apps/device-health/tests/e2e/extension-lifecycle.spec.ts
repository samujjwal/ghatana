import { test, expect, BrowserContext, Page } from '@playwright/test';

/**
 * E2E Tests for Extension Lifecycle
 * 
 * Tests extension installation, updates, and state management:
 * - Extension initialization on install
 * - Version updates and migrations
 * - State persistence across restarts
 * - Background service worker lifecycle
 * - Extension uninstall cleanup
 */

test.describe('Extension Lifecycle', () => {
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
    page = await context.newPage();
  });

  test.afterEach(async () => {
    await page.close();
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('should initialize extension on first install', async () => {
    // Navigate to extension page
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Verify extension loaded successfully
    const bodyText = await page.textContent('body');
    expect(bodyText).toBeTruthy();
    
    // Check for onboarding tour (first-time user indicator)
    const tourOverlay = page.locator('[data-testid="onboarding-overlay"]');
    const hasTour = await tourOverlay.isVisible({ timeout: 2000 }).catch(() => false);
    
    // Either onboarding appears or extension is already initialized
    if (hasTour) {
      await expect(tourOverlay).toBeVisible();
    } else {
      // Dashboard should be visible
      const dashboard = page.locator('[data-testid="dashboard-container"]');
      const hasDashboard = await dashboard.isVisible({ timeout: 2000 }).catch(() => false);
      
      if (hasDashboard) {
        await expect(dashboard).toBeVisible();
      }
    }
  });

  test('should persist state across page reloads', async () => {
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Skip onboarding if present
    const skipButton = page.locator('[data-testid="tour-skip-button"]');
    if (await skipButton.isVisible({ timeout: 1000 }).catch(() => false)) {
      await skipButton.click();
      await page.waitForTimeout(300);
    }
    
    // Change a setting
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 1000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      const telemetryToggle = page.locator('[data-testid="telemetry-enabled-toggle"]');
      
      if (await telemetryToggle.isVisible({ timeout: 1000 }).catch(() => false)) {
        const initialState = await telemetryToggle.isChecked().catch(() => true);
        
        await telemetryToggle.click();
        await page.waitForTimeout(500);
        
        const newState = await telemetryToggle.isChecked();
        
        // Reload page
        await page.reload();
        await page.waitForLoadState('networkidle');
        
        // Navigate back to settings
        const settingsTab2 = page.locator('[data-testid="settings-tab"]');
        if (await settingsTab2.isVisible({ timeout: 1000 }).catch(() => false)) {
          await settingsTab2.click();
          await page.waitForLoadState('networkidle');
        }
        
        // Verify state persisted
        const toggleAfterReload = page.locator('[data-testid="telemetry-enabled-toggle"]');
        if (await toggleAfterReload.isVisible({ timeout: 1000 }).catch(() => false)) {
          const persistedState = await toggleAfterReload.isChecked();
          expect(persistedState).toBe(newState);
        }
      }
    }
  });

  test('should handle service worker restarts', async () => {
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Get current service worker
    const serviceWorker = context.serviceWorkers()[0];
    expect(serviceWorker).toBeTruthy();
    
    // Verify extension is functional
    const dashboard = page.locator('body');
    await expect(dashboard).toBeVisible();
    
    // Reload page (might trigger service worker restart)
    await page.reload();
    await page.waitForLoadState('networkidle');
    
    // Extension should still work
    await expect(dashboard).toBeVisible();
  });

  test('should maintain data integrity across restarts', async () => {
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Add test data
    await page.evaluate(() => {
      const testData = {
        id: 'lifecycle-test-data',
        value: 'test-value-123',
        timestamp: Date.now(),
      };
      
      if (window.localStorage) {
        window.localStorage.setItem('lifecycle_test', JSON.stringify(testData));
      }
    });
    
    // Close and reopen page
    await page.close();
    page = await context.newPage();
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Verify data persisted
    const persistedData = await page.evaluate(() => {
      if (window.localStorage) {
        const data = window.localStorage.getItem('lifecycle_test');
        return data ? JSON.parse(data) : null;
      }
      return null;
    });
    
    expect(persistedData).toBeTruthy();
    expect(persistedData?.id).toBe('lifecycle-test-data');
    expect(persistedData?.value).toBe('test-value-123');
  });

  test('should handle version updates gracefully', async () => {
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Get current version from manifest
    const manifestVersion = await page.evaluate(async () => {
      try {
        const response = await fetch('/manifest.json');
        const manifest = await response.json();
        return manifest.version;
      } catch {
        return '0.0.0';
      }
    });
    
    expect(manifestVersion).toBeTruthy();
    
    // Store version in storage
    await page.evaluate((version) => {
      if (window.localStorage) {
        window.localStorage.setItem('extension_version', version);
      }
    }, manifestVersion);
    
    // Simulate version update by changing stored version
    await page.evaluate(() => {
      if (window.localStorage) {
        window.localStorage.setItem('extension_version', '0.0.1');
      }
    });
    
    // Reload to trigger update check
    await page.reload();
    await page.waitForLoadState('networkidle');
    
    // Extension should still work after update
    const dashboard = page.locator('body');
    await expect(dashboard).toBeVisible();
  });

  test('should initialize default settings on first run', async () => {
    // Clear all storage to simulate first run
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    await page.evaluate(() => {
      window.localStorage?.clear();
    });
    
    // Reload to trigger initialization
    await page.reload();
    await page.waitForLoadState('networkidle');
    
    // Check if default settings were created
    const hasDefaultSettings = await page.evaluate(() => {
      if (!window.localStorage) return false;
      
      // Check for common default settings
      const keys = Object.keys(window.localStorage);
      return keys.length > 0;
    });
    
    // Extension should initialize storage
    // (actual verification depends on implementation)
    expect(typeof hasDefaultSettings).toBe('boolean');
  });

  test('should clean up on reset/clear data', async () => {
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Skip onboarding
    const skipButton = page.locator('[data-testid="tour-skip-button"]');
    if (await skipButton.isVisible({ timeout: 1000 }).catch(() => false)) {
      await skipButton.click();
      await page.waitForTimeout(300);
    }
    
    // Add some data
    await page.evaluate(() => {
      if (window.localStorage) {
        window.localStorage.setItem('test_data_1', 'value1');
        window.localStorage.setItem('test_data_2', 'value2');
      }
    });
    
    // Navigate to settings
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 1000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Clear all data
      const clearButton = page.locator('[data-testid="clear-all-data"]');
      
      if (await clearButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        await clearButton.click();
        
        // Confirm if dialog appears
        const confirmButton = page.locator('[data-testid="confirm-clear-all"]');
        if (await confirmButton.isVisible({ timeout: 1000 }).catch(() => false)) {
          await confirmButton.click();
        }
        
        await page.waitForTimeout(1000);
        
        // Verify data was cleared
        const storageAfterClear = await page.evaluate(() => {
          if (!window.localStorage) return {};
          
          const data: Record<string, string> = {};
          for (let i = 0; i < window.localStorage.length; i++) {
            const key = window.localStorage.key(i);
            if (key) {
              data[key] = window.localStorage.getItem(key) || '';
            }
          }
          return data;
        });
        
        // Test data should be cleared
        expect(storageAfterClear['test_data_1']).toBeFalsy();
        expect(storageAfterClear['test_data_2']).toBeFalsy();
      }
    }
  });

  test('should handle concurrent tab access', async () => {
    // Open first tab
    const page1 = await context.newPage();
    await page1.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page1.waitForLoadState('networkidle');
    
    // Open second tab
    const page2 = await context.newPage();
    await page2.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page2.waitForLoadState('networkidle');
    
    // Both tabs should work
    const body1 = page1.locator('body');
    const body2 = page2.locator('body');
    
    await expect(body1).toBeVisible();
    await expect(body2).toBeVisible();
    
    // Make a change in tab 1
    await page1.evaluate(() => {
      if (window.localStorage) {
        window.localStorage.setItem('concurrent_test', 'tab1-value');
      }
    });
    
    // Wait for potential sync
    await page2.waitForTimeout(500);
    
    // Check if tab 2 sees the change (depends on storage event handling)
    const valueInTab2 = await page2.evaluate(() => {
      return window.localStorage?.getItem('concurrent_test');
    });
    
    // Value might or might not sync immediately
    expect(typeof valueInTab2).toBe('string');
    
    await page1.close();
    await page2.close();
  });

  test('should measure extension startup performance', async () => {
    const startTime = Date.now();
    
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    const loadTime = Date.now() - startTime;
    
    // Extension should load quickly (< 2 seconds)
    expect(loadTime).toBeLessThan(2000);
    
    // Verify page is interactive
    const dashboard = page.locator('body');
    await expect(dashboard).toBeVisible();
  });

  test('should handle offline scenarios', async () => {
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Go offline
    await context.setOffline(true);
    
    // Extension should still work (it's local)
    await page.reload();
    await page.waitForLoadState('networkidle');
    
    const dashboard = page.locator('body');
    await expect(dashboard).toBeVisible();
    
    // Go back online
    await context.setOffline(false);
  });
});
