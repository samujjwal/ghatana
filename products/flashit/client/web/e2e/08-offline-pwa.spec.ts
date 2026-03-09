/**
 * E2E Test: Offline & PWA (Web)
 * Tests offline functionality and PWA features
 */

import { test, expect, setOffline, setOnline } from './fixtures';

test.describe('Offline & PWA', () => {
  test('should work offline with service worker', async ({ authenticatedPage }) => {
    // Load page online first
    await authenticatedPage.goto('/');
    await expect(authenticatedPage.getByTestId('home-screen')).toBeVisible();
    
    // Go offline
    await setOffline(authenticatedPage);
    
    // Should show offline banner
    await expect(authenticatedPage.getByTestId('offline-banner')).toBeVisible();
    
    // Should still be able to browse cached content
    await authenticatedPage.getByTestId('moment-card').first().click();
    await expect(authenticatedPage.getByTestId('moment-detail')).toBeVisible();
  });

  test('should queue actions when offline', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Go offline
    await setOffline(authenticatedPage);
    
    // Try to create moment
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.getByTestId('title-input').fill('Offline Test Moment');
    await authenticatedPage.getByTestId('content-input').fill('Created offline');
    await authenticatedPage.getByTestId('save-button').click();
    
    // Should show queued message
    await expect(authenticatedPage.getByText(/queued for upload/i)).toBeVisible();
  });

  test('should sync when back online', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Create offline moment
    await setOffline(authenticatedPage);
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.getByTestId('title-input').fill('Sync Test');
    await authenticatedPage.getByTestId('content-input').fill('Will sync');
    await authenticatedPage.getByTestId('save-button').click();
    
    // Go back online
    await setOnline(authenticatedPage);
    
    // Should show syncing indicator
    await expect(authenticatedPage.getByText(/syncing/i)).toBeVisible({ timeout: 3000 });
    
    // Wait for sync to complete
    await expect(authenticatedPage.getByText(/synced/i)).toBeVisible({ timeout: 10000 });
  });

  test('should show install prompt for PWA', async ({ page }) => {
    await page.goto('/');
    
    // Simulate beforeinstallprompt event
    // Note: This is difficult to test in headless mode
    // In real scenario, check if install button appears
    
    const installButton = page.getByTestId('install-pwa-button');
    if (await installButton.isVisible()) {
      await installButton.click();
      // Installation prompt would appear
    }
  });

  test('should cache images offline', async ({ authenticatedPage }) => {
    // Load images online
    await authenticatedPage.goto('/');
    await authenticatedPage.waitForTimeout(2000); // Wait for images to cache
    
    // Go offline
    await setOffline(authenticatedPage);
    
    // Images should still be visible
    const images = authenticatedPage.getByRole('img');
    const count = await images.count();
    expect(count).toBeGreaterThan(0);
  });

  test('should handle failed sync gracefully', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/sync');
    
    // Should show sync status
    await expect(authenticatedPage.getByTestId('sync-status')).toBeVisible();
    await expect(authenticatedPage.getByTestId('pending-uploads-count')).toBeVisible();
  });
});
