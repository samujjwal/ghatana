/**
 * E2E Test: Settings & Profile Flow
 * Tests user settings, profile management, and preferences
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, takeScreenshot } = require('./helpers/setup');

describe('Settings & Profile Flow', () => {
  beforeAll(async () => {
    await device.launchApp();
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('should navigate to settings', async () => {
    await element(by.id('settings-tab')).tap();
    
    await expect(element(by.id('settings-screen'))).toBeVisible();
    await expect(element(by.id('profile-section'))).toBeVisible();
    await expect(element(by.id('preferences-section'))).toBeVisible();
    
    await takeScreenshot('settings-screen');
  });

  it('should update profile name', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('edit-profile-button')).tap();
    
    await element(by.id('name-input')).clearText();
    await element(by.id('name-input')).typeText('Updated Name');
    await element(by.id('save-profile-button')).tap();
    
    await waitFor(element(by.text('Profile updated')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should update profile picture', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('edit-profile-button')).tap();
    
    await element(by.id('change-avatar-button')).tap();
    
    // Select from gallery (mock)
    await element(by.id('select-from-gallery')).tap();
    
    // Crop and save (simplified)
    await element(by.id('save-avatar-button')).tap();
    
    await waitFor(element(by.text('Profile picture updated')))
      .toBeVisible()
      .withTimeout(5000);
  });

  it('should toggle dark mode', async () => {
    await element(by.id('settings-tab')).tap();
    
    // Toggle dark mode
    await element(by.id('dark-mode-toggle')).tap();
    
    // Should see theme change
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    await takeScreenshot('dark-mode-enabled');
    
    // Toggle back
    await element(by.id('dark-mode-toggle')).tap();
  });

  it('should change notification settings', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('notifications-settings-button')).tap();
    
    await expect(element(by.id('notifications-screen'))).toBeVisible();
    
    // Toggle push notifications
    await element(by.id('push-notifications-toggle')).tap();
    
    // Toggle email notifications
    await element(by.id('email-notifications-toggle')).tap();
    
    await takeScreenshot('notification-settings');
  });

  it('should change privacy settings', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('privacy-settings-button')).tap();
    
    await expect(element(by.id('privacy-screen'))).toBeVisible();
    
    // Set profile visibility
    await element(by.id('profile-visibility-select')).tap();
    await element(by.id('visibility-private')).tap();
    
    await waitFor(element(by.text('Privacy settings updated')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should view storage usage', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('storage-info-button')).tap();
    
    await expect(element(by.id('storage-screen'))).toBeVisible();
    await expect(element(by.id('total-storage-used'))).toBeVisible();
    await expect(element(by.id('storage-breakdown'))).toBeVisible();
    
    await takeScreenshot('storage-usage');
  });

  it('should export user data', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('data-settings-button')).tap();
    
    await element(by.id('export-data-button')).tap();
    
    // Confirm export
    await element(by.text('Export')).tap();
    
    await waitFor(element(by.text('Export started')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should change app language', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('language-settings-button')).tap();
    
    await expect(element(by.id('language-screen'))).toBeVisible();
    
    // Select Spanish
    await element(by.id('language-es')).tap();
    
    // Should show confirmation
    await waitFor(element(by.text('Language updated')))
      .toBeVisible()
      .withTimeout(3000);
    
    // Change back to English
    await element(by.id('language-en')).tap();
  });

  it('should access help & support', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('help-support-button')).tap();
    
    await expect(element(by.id('help-screen'))).toBeVisible();
    await expect(element(by.id('faq-section'))).toBeVisible();
    await expect(element(by.id('contact-support-button'))).toBeVisible();
    
    await takeScreenshot('help-support');
  });

  it('should view app version', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('about-button')).tap();
    
    await expect(element(by.id('about-screen'))).toBeVisible();
    await expect(element(by.id('app-version'))).toBeVisible();
    await expect(element(by.id('privacy-policy-link'))).toBeVisible();
    await expect(element(by.id('terms-link'))).toBeVisible();
  });

  it('should delete account', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('account-settings-button')).tap();
    
    await element(by.id('delete-account-button')).tap();
    
    // Should show confirmation dialog
    await expect(element(by.text('Are you sure?'))).toBeVisible();
    
    // Cancel (don't actually delete in test)
    await element(by.text('Cancel')).tap();
  });
});
