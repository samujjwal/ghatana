/**
 * E2E Test: Settings & Profile (Web)
 * Tests user settings and profile management
 */

import { test, expect } from './fixtures';

test.describe('Settings & Profile', () => {
  test('should view settings page', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings');
    
    await expect(authenticatedPage.getByTestId('settings-screen')).toBeVisible();
    await expect(authenticatedPage.getByTestId('profile-section')).toBeVisible();
  });

  test('should update profile name', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/profile');
    
    await authenticatedPage.getByTestId('name-input').fill('Updated Test Name');
    await authenticatedPage.getByTestId('save-profile-button').click();
    
    await expect(authenticatedPage.getByText(/profile updated/i)).toBeVisible();
  });

  test('should update profile picture', async ({ authenticatedPage, uploadFile }) => {
    await authenticatedPage.goto('/settings/profile');
    
    await uploadFile(authenticatedPage, '[data-testid="avatar-upload"]', 'e2e/fixtures/avatar.jpg');
    
    await authenticatedPage.getByTestId('save-avatar-button').click();
    
    await expect(authenticatedPage.getByText(/profile picture updated/i)).toBeVisible();
  });

  test('should toggle dark mode', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings');
    
    const darkModeToggle = authenticatedPage.getByTestId('dark-mode-toggle');
    await darkModeToggle.click();
    
    // Check if dark class is applied
    const html = authenticatedPage.locator('html');
    await expect(html).toHaveClass(/dark/);
  });

  test('should change notification preferences', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/notifications');
    
    await authenticatedPage.getByTestId('email-notifications-toggle').click();
    await authenticatedPage.getByTestId('push-notifications-toggle').click();
    
    await expect(authenticatedPage.getByText(/preferences updated/i)).toBeVisible();
  });

  test('should change privacy settings', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/privacy');
    
    await authenticatedPage.getByTestId('profile-visibility-select').selectOption('private');
    await authenticatedPage.getByTestId('save-privacy-button').click();
    
    await expect(authenticatedPage.getByText(/privacy settings updated/i)).toBeVisible();
  });

  test('should view storage usage', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/storage');
    
    await expect(authenticatedPage.getByTestId('total-storage')).toBeVisible();
    await expect(authenticatedPage.getByTestId('storage-chart')).toBeVisible();
  });

  test('should export user data', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/data');
    
    await authenticatedPage.getByTestId('export-data-button').click();
    await authenticatedPage.getByRole('button', { name: /confirm/i }).click();
    
    await expect(authenticatedPage.getByText(/export started/i)).toBeVisible();
  });

  test('should change password', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/security');
    
    await authenticatedPage.getByTestId('current-password-input').fill('Test1234!');
    await authenticatedPage.getByTestId('new-password-input').fill('NewTest1234!');
    await authenticatedPage.getByTestId('confirm-new-password-input').fill('NewTest1234!');
    await authenticatedPage.getByTestId('change-password-button').click();
    
    await expect(authenticatedPage.getByText(/password updated/i)).toBeVisible();
  });

  test('should enable two-factor authentication', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/security');
    
    await authenticatedPage.getByTestId('enable-2fa-button').click();
    
    // Should show QR code
    await expect(authenticatedPage.getByTestId('2fa-qr-code')).toBeVisible();
    await expect(authenticatedPage.getByTestId('2fa-code-input')).toBeVisible();
  });
});
