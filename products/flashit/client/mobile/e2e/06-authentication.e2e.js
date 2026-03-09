/**
 * E2E Test: Authentication Flow
 * Tests login, signup, logout, and password reset
 */

const { device, element, by, waitFor } = require('detox');
const { clearAppData, takeScreenshot } = require('./helpers/setup');

describe('Authentication Flow', () => {
  beforeAll(async () => {
    await device.launchApp();
  });

  beforeEach(async () => {
    await clearAppData();
  });

  it('should display login screen on first launch', async () => {
    await expect(element(by.id('login-screen'))).toBeVisible();
    await expect(element(by.id('email-input'))).toBeVisible();
    await expect(element(by.id('password-input'))).toBeVisible();
    await expect(element(by.id('login-button'))).toBeVisible();
    
    await takeScreenshot('login-screen');
  });

  it('should login with valid credentials', async () => {
    await element(by.id('email-input')).typeText('test@flashit.app');
    await element(by.id('password-input')).typeText('Test1234!');
    await element(by.id('login-button')).tap();
    
    // Should navigate to home
    await waitFor(element(by.id('home-screen')))
      .toBeVisible()
      .withTimeout(5000);
    
    await takeScreenshot('logged-in');
  });

  it('should show error for invalid credentials', async () => {
    await element(by.id('email-input')).typeText('wrong@email.com');
    await element(by.id('password-input')).typeText('wrongpass');
    await element(by.id('login-button')).tap();
    
    await expect(element(by.text('Invalid email or password'))).toBeVisible();
  });

  it('should validate email format', async () => {
    await element(by.id('email-input')).typeText('invalid-email');
    await element(by.id('password-input')).typeText('Test1234!');
    await element(by.id('login-button')).tap();
    
    await expect(element(by.text('Please enter a valid email'))).toBeVisible();
  });

  it('should navigate to signup screen', async () => {
    await element(by.id('signup-link')).tap();
    
    await expect(element(by.id('signup-screen'))).toBeVisible();
    await expect(element(by.id('name-input'))).toBeVisible();
    await expect(element(by.id('email-input'))).toBeVisible();
    await expect(element(by.id('password-input'))).toBeVisible();
    await expect(element(by.id('signup-button'))).toBeVisible();
    
    await takeScreenshot('signup-screen');
  });

  it('should signup with valid data', async () => {
    await element(by.id('signup-link')).tap();
    
    const timestamp = Date.now();
    await element(by.id('name-input')).typeText('Test User');
    await element(by.id('email-input')).typeText(`test${timestamp}@flashit.app`);
    await element(by.id('password-input')).typeText('Test1234!');
    await element(by.id('confirm-password-input')).typeText('Test1234!');
    await element(by.id('signup-button')).tap();
    
    // Should navigate to home or email verification
    await waitFor(element(by.id('home-screen')))
      .toBeVisible()
      .withTimeout(5000);
  });

  it('should validate password strength', async () => {
    await element(by.id('signup-link')).tap();
    
    await element(by.id('password-input')).typeText('weak');
    
    await expect(element(by.text('Password must be at least 8 characters'))).toBeVisible();
  });

  it('should validate password confirmation match', async () => {
    await element(by.id('signup-link')).tap();
    
    await element(by.id('password-input')).typeText('Test1234!');
    await element(by.id('confirm-password-input')).typeText('Different123!');
    await element(by.id('signup-button')).tap();
    
    await expect(element(by.text('Passwords do not match'))).toBeVisible();
  });

  it('should navigate to forgot password screen', async () => {
    await element(by.id('forgot-password-link')).tap();
    
    await expect(element(by.id('forgot-password-screen'))).toBeVisible();
    await expect(element(by.id('email-input'))).toBeVisible();
    await expect(element(by.id('reset-button'))).toBeVisible();
    
    await takeScreenshot('forgot-password-screen');
  });

  it('should request password reset', async () => {
    await element(by.id('forgot-password-link')).tap();
    
    await element(by.id('email-input')).typeText('test@flashit.app');
    await element(by.id('reset-button')).tap();
    
    await waitFor(element(by.text('Password reset link sent')))
      .toBeVisible()
      .withTimeout(5000);
  });

  it('should logout', async () => {
    // Login first
    await element(by.id('email-input')).typeText('test@flashit.app');
    await element(by.id('password-input')).typeText('Test1234!');
    await element(by.id('login-button')).tap();
    await waitFor(element(by.id('home-screen'))).toBeVisible().withTimeout(5000);
    
    // Logout
    await element(by.id('settings-tab')).tap();
    await element(by.id('logout-button')).tap();
    
    // Confirm logout
    await element(by.text('Logout')).tap();
    
    // Should return to login screen
    await waitFor(element(by.id('login-screen')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should persist login session', async () => {
    // Login
    await element(by.id('email-input')).typeText('test@flashit.app');
    await element(by.id('password-input')).typeText('Test1234!');
    await element(by.id('login-button')).tap();
    await waitFor(element(by.id('home-screen'))).toBeVisible().withTimeout(5000);
    
    // Reload app
    await device.reloadReactNative();
    
    // Should still be logged in
    await expect(element(by.id('home-screen'))).toBeVisible();
  });
});
