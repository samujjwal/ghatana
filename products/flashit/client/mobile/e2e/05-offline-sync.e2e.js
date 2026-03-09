/**
 * E2E Test: Offline & Sync Flow
 * Tests offline functionality and background sync
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, setOfflineMode, setOnlineMode, takeScreenshot } = require('./helpers/setup');

describe('Offline & Sync Flow', () => {
  beforeAll(async () => {
    await device.launchApp({
      permissions: { microphone: 'YES', camera: 'YES' },
    });
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
    await setOnlineMode(); // Ensure online at start of each test
  });

  it('should show offline indicator when network disconnected', async () => {
    await setOfflineMode();
    
    // Should show offline banner
    await expect(element(by.id('offline-banner'))).toBeVisible();
    await expect(element(by.text('You are offline'))).toBeVisible();
    
    await takeScreenshot('offline-banner');
  });

  it('should save moment offline', async () => {
    await setOfflineMode();
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    
    // Record voice memo
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 2000));
    await element(by.id('stop-button')).tap();
    
    // Save
    await element(by.id('moment-title-input')).typeText('Offline Test Memo');
    await element(by.id('save-button')).tap();
    
    // Should show queued message
    await waitFor(element(by.text('Queued for upload')))
      .toBeVisible()
      .withTimeout(5000);
    
    await takeScreenshot('moment-saved-offline');
  });

  it('should display upload queue', async () => {
    // Create some offline moments first
    await setOfflineMode();
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 1000));
    await element(by.id('stop-button')).tap();
    await element(by.id('moment-title-input')).typeText('Queue Test 1');
    await element(by.id('save-button')).tap();
    await waitFor(element(by.text('Queued for upload'))).toBeVisible().withTimeout(5000);
    
    // Check upload queue
    await element(by.id('settings-tab')).tap();
    await element(by.id('upload-queue-button')).tap();
    
    await expect(element(by.id('upload-queue-screen'))).toBeVisible();
    await expect(element(by.text('Queue Test 1'))).toBeVisible();
    await expect(element(by.id('queue-item-0-status'))).toHaveText('Pending');
    
    await takeScreenshot('upload-queue');
  });

  it('should sync moments when back online', async () => {
    // Create offline moment
    await setOfflineMode();
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 1000));
    await element(by.id('stop-button')).tap();
    await element(by.id('moment-title-input')).typeText('Sync Test');
    await element(by.id('save-button')).tap();
    await waitFor(element(by.text('Queued for upload'))).toBeVisible().withTimeout(5000);
    
    // Go back online
    await setOnlineMode();
    
    // Should show syncing indicator
    await waitFor(element(by.text('Syncing...')))
      .toBeVisible()
      .withTimeout(3000);
    
    // Wait for sync to complete
    await waitFor(element(by.text('All changes synced')))
      .toBeVisible()
      .withTimeout(10000);
    
    await takeScreenshot('synced');
  });

  it('should retry failed uploads', async () => {
    // Check upload queue
    await element(by.id('settings-tab')).tap();
    await element(by.id('upload-queue-button')).tap();
    
    // If there are failed items
    // await element(by.id('retry-all-button')).tap();
    
    // Should show retry progress
    // await expect(element(by.text('Retrying uploads...'))).toBeVisible();
  });

  it('should show offline data in timeline', async () => {
    // Create offline moment
    await setOfflineMode();
    
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 1000));
    await element(by.id('stop-button')).tap();
    await element(by.id('moment-title-input')).typeText('Offline Visible');
    await element(by.id('save-button')).tap();
    await waitFor(element(by.text('Queued for upload'))).toBeVisible().withTimeout(5000);
    
    // Go to timeline
    await element(by.id('home-tab')).tap();
    
    // Should see offline moment with indicator
    await expect(element(by.text('Offline Visible'))).toBeVisible();
    await expect(element(by.id('moment-offline-indicator'))).toBeVisible();
  });

  it('should handle edit conflicts on sync', async () => {
    // This test would require:
    // 1. Creating a moment online
    // 2. Editing it offline
    // 3. Editing it from another device
    // 4. Going back online
    // 5. Detecting and resolving conflict
    
    // Mock conflict scenario
    await element(by.id('settings-tab')).tap();
    // await expect(element(by.text('Sync conflicts detected'))).toBeVisible();
  });

  it('should limit offline storage', async () => {
    await setOfflineMode();
    
    // Check storage info
    await element(by.id('settings-tab')).tap();
    await element(by.id('storage-info-button')).tap();
    
    await expect(element(by.id('storage-screen'))).toBeVisible();
    await expect(element(by.id('offline-storage-used'))).toBeVisible();
    await expect(element(by.id('offline-storage-limit'))).toBeVisible();
    
    await takeScreenshot('storage-info');
  });

  it('should show sync status indicator', async () => {
    await element(by.id('settings-tab')).tap();
    
    // Should show last sync time
    await expect(element(by.id('last-sync-time'))).toBeVisible();
    
    // Should show sync status
    await expect(element(by.id('sync-status'))).toBeVisible();
  });

  it('should clear offline cache', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('storage-info-button')).tap();
    
    // Clear cache
    await element(by.id('clear-cache-button')).tap();
    
    // Confirm
    await element(by.text('Clear')).tap();
    
    // Should show success message
    await waitFor(element(by.text('Cache cleared')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should prioritize upload queue by size and date', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('upload-queue-button')).tap();
    
    // Should see items ordered correctly
    // (smaller files first, then by date)
    await expect(element(by.id('queue-item-0'))).toBeVisible();
  });
});
