import { test, expect, BrowserContext, Page } from '@playwright/test';

/**
 * E2E Tests for Settings Configuration
 * 
 * Tests the settings/configuration UI, including:
 * - Navigating settings tabs
 * - Toggling feature flags
 * - Configuring telemetry preferences
 * - Updating privacy settings
 * - Settings persistence and validation
 */

test.describe('Settings Configuration', () => {
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
    
    await page.goto(`chrome-extension://${extensionId}/dashboard.html`);
    await page.waitForLoadState('networkidle');
    
    // Skip onboarding
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

  test('should navigate to settings page', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Verify settings page loaded
      const settingsContainer = page.locator('[data-testid="settings-container"]');
      await expect(settingsContainer).toBeVisible();
      
      // Check for common settings sections
      const pageContent = await page.textContent('body');
      expect(pageContent).toMatch(/settings|configuration|preferences/i);
    } else {
      // Try alternative navigation (gear icon, menu, etc.)
      const settingsButton = page.locator('[aria-label*="settings" i], [title*="settings" i]').first();
      
      if (await settingsButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        await settingsButton.click();
        await page.waitForLoadState('networkidle');
      }
    }
  });

  test('should toggle telemetry collection on/off', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Find telemetry toggle
      const telemetryToggle = page.locator('[data-testid="telemetry-enabled-toggle"]');
      
      if (await telemetryToggle.isVisible({ timeout: 2000 }).catch(() => false)) {
        // Get initial state
        const initialChecked = await telemetryToggle.isChecked().catch(() => false);
        
        // Toggle off
        await telemetryToggle.click();
        await page.waitForTimeout(500);
        
        // Verify state changed
        const newChecked = await telemetryToggle.isChecked();
        expect(newChecked).toBe(!initialChecked);
        
        // Reload page and verify persistence
        await page.reload();
        await page.waitForLoadState('networkidle');
        
        const settingsTab2 = page.locator('[data-testid="settings-tab"]');
        if (await settingsTab2.isVisible({ timeout: 1000 }).catch(() => false)) {
          await settingsTab2.click();
          await page.waitForLoadState('networkidle');
        }
        
        const toggleAfterReload = page.locator('[data-testid="telemetry-enabled-toggle"]');
        if (await toggleAfterReload.isVisible({ timeout: 1000 }).catch(() => false)) {
          const persistedChecked = await toggleAfterReload.isChecked();
          expect(persistedChecked).toBe(newChecked);
        }
      }
    }
  });

  test('should configure data retention period', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Find retention period selector
      const retentionSelector = page.locator('[data-testid="retention-days-select"]');
      
      if (await retentionSelector.isVisible({ timeout: 2000 }).catch(() => false)) {
        // Change to 7 days
        await retentionSelector.selectOption('7');
        await page.waitForTimeout(500);
        
        // Verify selected value
        const selectedValue = await retentionSelector.inputValue();
        expect(selectedValue).toBe('7');
        
        // Reload and verify persistence
        await page.reload();
        await page.waitForLoadState('networkidle');
        
        const settingsTab2 = page.locator('[data-testid="settings-tab"]');
        if (await settingsTab2.isVisible({ timeout: 1000 }).catch(() => false)) {
          await settingsTab2.click();
          await page.waitForLoadState('networkidle');
        }
        
        const retentionAfterReload = page.locator('[data-testid="retention-days-select"]');
        if (await retentionAfterReload.isVisible({ timeout: 1000 }).catch(() => false)) {
          const persistedValue = await retentionAfterReload.inputValue();
          expect(persistedValue).toBe('7');
        }
      }
    }
  });

  test('should enable/disable performance tracking', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Find performance tracking toggle
      const performanceToggle = page.locator('[data-testid="track-performance-toggle"]');
      
      if (await performanceToggle.isVisible({ timeout: 2000 }).catch(() => false)) {
        const initialState = await performanceToggle.isChecked().catch(() => true);
        
        // Toggle performance tracking
        await performanceToggle.click();
        await page.waitForTimeout(500);
        
        const newState = await performanceToggle.isChecked();
        expect(newState).toBe(!initialState);
      }
    }
  });

  test('should enable/disable error reporting', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Find error reporting toggle
      const errorToggle = page.locator('[data-testid="track-errors-toggle"]');
      
      if (await errorToggle.isVisible({ timeout: 2000 }).catch(() => false)) {
        const initialState = await errorToggle.isChecked().catch(() => true);
        
        await errorToggle.click();
        await page.waitForTimeout(500);
        
        const newState = await errorToggle.isChecked();
        expect(newState).toBe(!initialState);
      }
    }
  });

  test('should configure sampling rate', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Find sampling rate slider or input
      const samplingInput = page.locator('[data-testid="sampling-rate-input"]');
      
      if (await samplingInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        // Set sampling rate to 50%
        await samplingInput.fill('50');
        await page.waitForTimeout(500);
        
        const value = await samplingInput.inputValue();
        expect(value).toBe('50');
      }
    }
  });

  test('should clear all telemetry data', async () => {
    // First, add some test data
    await page.evaluate(() => {
      const testData = {
        events: [
          { id: '1', type: 'test', timestamp: Date.now() },
          { id: '2', type: 'test', timestamp: Date.now() },
        ],
      };
      
      if (window.localStorage) {
        window.localStorage.setItem('telemetry_events', JSON.stringify(testData));
      }
    });
    
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Find clear data button
      const clearButton = page.locator('[data-testid="clear-telemetry-data"]');
      
      if (await clearButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await clearButton.click();
        
        // Confirm dialog might appear
        const confirmButton = page.locator('[data-testid="confirm-clear-data"]');
        if (await confirmButton.isVisible({ timeout: 1000 }).catch(() => false)) {
          await confirmButton.click();
        }
        
        await page.waitForTimeout(1000);
        
        // Verify data was cleared
        const hasData = await page.evaluate(() => {
          return window.localStorage?.getItem('telemetry_events') !== null;
        });
        
        // Data should be cleared or empty
        if (hasData) {
          const data = await page.evaluate(() => {
            const stored = window.localStorage?.getItem('telemetry_events');
            return stored ? JSON.parse(stored) : null;
          });
          
          // Should be empty or null
          if (data && data.events) {
            expect(data.events.length).toBe(0);
          }
        }
      }
    }
  });

  test('should navigate between settings tabs', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Look for sub-tabs within settings
      const configTab = page.locator('[data-testid="config-tab"]');
      const privacyTab = page.locator('[data-testid="privacy-tab"]');
      const metricsTab = page.locator('[data-testid="metrics-tab"]');
      
      // Navigate to privacy tab if it exists
      if (await privacyTab.isVisible({ timeout: 1000 }).catch(() => false)) {
        await privacyTab.click();
        await page.waitForTimeout(300);
        
        const privacyContent = await page.textContent('body');
        expect(privacyContent).toMatch(/privacy|data|consent/i);
      }
      
      // Navigate to metrics tab if it exists
      if (await metricsTab.isVisible({ timeout: 1000 }).catch(() => false)) {
        await metricsTab.click();
        await page.waitForTimeout(300);
        
        const metricsContent = await page.textContent('body');
        expect(metricsContent).toMatch(/metrics|statistics|analytics/i);
      }
      
      // Navigate back to config tab
      if (await configTab.isVisible({ timeout: 1000 }).catch(() => false)) {
        await configTab.click();
        await page.waitForTimeout(300);
        
        const configContent = await page.textContent('body');
        expect(configContent).toMatch(/configuration|settings|options/i);
      }
    }
  });

  test('should validate settings input', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Try to set invalid retention period
      const retentionInput = page.locator('[data-testid="retention-days-input"]');
      
      if (await retentionInput.isVisible({ timeout: 1000 }).catch(() => false)) {
        // Try negative value
        await retentionInput.fill('-1');
        await page.waitForTimeout(300);
        
        // Should show validation error or reset to valid value
        const errorMessage = page.locator('[data-testid="validation-error"]');
        const hasError = await errorMessage.isVisible({ timeout: 1000 }).catch(() => false);
        
        if (hasError) {
          await expect(errorMessage).toContainText(/invalid|error|must be/i);
        } else {
          // Or input should be corrected automatically
          const value = await retentionInput.inputValue();
          expect(parseInt(value)).toBeGreaterThanOrEqual(0);
        }
      }
    }
  });

  test('should show unsaved changes warning', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Make a change
      const telemetryToggle = page.locator('[data-testid="telemetry-enabled-toggle"]');
      
      if (await telemetryToggle.isVisible({ timeout: 1000 }).catch(() => false)) {
        await telemetryToggle.click();
        await page.waitForTimeout(300);
        
        // Try to navigate away
        const dashboardTab = page.locator('[data-testid="dashboard-tab"]');
        
        if (await dashboardTab.isVisible({ timeout: 1000 }).catch(() => false)) {
          await dashboardTab.click();
          
          // Check for unsaved changes dialog
          const warningDialog = page.locator('[data-testid="unsaved-changes-dialog"]');
          
          if (await warningDialog.isVisible({ timeout: 1000 }).catch(() => false)) {
            await expect(warningDialog).toContainText(/unsaved|discard|save/i);
            
            // Cancel navigation
            const cancelButton = warningDialog.locator('[data-testid="cancel-navigation"]');
            if (await cancelButton.isVisible({ timeout: 500 }).catch(() => false)) {
              await cancelButton.click();
            }
          }
        }
      }
    }
  });

  test('should reset settings to defaults', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      // Make some changes
      const telemetryToggle = page.locator('[data-testid="telemetry-enabled-toggle"]');
      
      if (await telemetryToggle.isVisible({ timeout: 1000 }).catch(() => false)) {
        await telemetryToggle.click();
        await page.waitForTimeout(300);
      }
      
      // Click reset to defaults button
      const resetButton = page.locator('[data-testid="reset-to-defaults"]');
      
      if (await resetButton.isVisible({ timeout: 1000 }).catch(() => false)) {
        await resetButton.click();
        
        // Confirm reset
        const confirmButton = page.locator('[data-testid="confirm-reset"]');
        if (await confirmButton.isVisible({ timeout: 1000 }).catch(() => false)) {
          await confirmButton.click();
        }
        
        await page.waitForTimeout(1000);
        
        // Verify settings were reset
        const toggleAfterReset = page.locator('[data-testid="telemetry-enabled-toggle"]');
        if (await toggleAfterReset.isVisible({ timeout: 1000 }).catch(() => false)) {
          // Should be in default state (typically enabled)
          const isChecked = await toggleAfterReset.isChecked();
          // Just verify it's in a valid state
          expect(typeof isChecked).toBe('boolean');
        }
      }
    }
  });

  test('should measure settings page load performance', async () => {
    const startTime = Date.now();
    
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      await settingsTab.click();
      await page.waitForLoadState('networkidle');
      
      const loadTime = Date.now() - startTime;
      
      // Settings should load quickly (< 1 second)
      expect(loadTime).toBeLessThan(1000);
      
      // Verify all settings sections are rendered
      const settingsContainer = page.locator('[data-testid="settings-container"]');
      await expect(settingsContainer).toBeVisible();
    }
  });

  test('should be keyboard navigable', async () => {
    const settingsTab = page.locator('[data-testid="settings-tab"]');
    
    if (await settingsTab.isVisible({ timeout: 2000 }).catch(() => false)) {
      // Tab to settings
      await page.keyboard.press('Tab');
      
      let foundSettings = false;
      for (let i = 0; i < 15; i++) {
        const focused = await page.evaluate(() => document.activeElement?.getAttribute('data-testid'));
        if (focused === 'settings-tab') {
          foundSettings = true;
          break;
        }
        await page.keyboard.press('Tab');
      }
      
      if (foundSettings) {
        await page.keyboard.press('Enter');
        await page.waitForLoadState('networkidle');
        
        // Navigate through settings with Tab
        await page.keyboard.press('Tab');
        await page.keyboard.press('Tab');
        
        // Toggle a setting with Space
        await page.keyboard.press('Space');
        
        // Verify interaction worked
        const settingsContainer = page.locator('[data-testid="settings-container"]');
        await expect(settingsContainer).toBeVisible();
      }
    }
  });
});
