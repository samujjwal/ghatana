/**
 * E2E Test: Authentication Flow (Web)
 * Tests login, signup, logout, and password reset on web
 */

import { test, expect } from './fixtures';

test.describe('Authentication Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display login page', async ({ page }) => {
    await page.goto('/login');
    
    await expect(page.getByTestId('login-form')).toBeVisible();
    await expect(page.getByTestId('email-input')).toBeVisible();
    await expect(page.getByTestId('password-input')).toBeVisible();
    await expect(page.getByTestId('login-button')).toBeVisible();
    
    await page.screenshot({ path: 'screenshots/web-login.png' });
  });

  test('should login with valid credentials', async ({ page, testUser }) => {
    await page.goto('/login');
    
    await page.getByTestId('email-input').fill(testUser.email);
    await page.getByTestId('password-input').fill(testUser.password);
    await page.getByTestId('login-button').click();
    
    // Should navigate to home
    await expect(page).toHaveURL('/', { timeout: 5000 });
    await expect(page.getByTestId('home-screen')).toBeVisible();
  });

  test('should show error for invalid credentials', async ({ page }) => {
    await page.goto('/login');
    
    await page.getByTestId('email-input').fill('wrong@email.com');
    await page.getByTestId('password-input').fill('wrongpass');
    await page.getByTestId('login-button').click();
    
    await expect(page.getByText('Invalid email or password')).toBeVisible();
  });

  test('should validate email format', async ({ page }) => {
    await page.goto('/login');
    
    await page.getByTestId('email-input').fill('invalid-email');
    await page.getByTestId('login-button').click();
    
    await expect(page.getByText(/valid email/i)).toBeVisible();
  });

  test('should navigate to signup page', async ({ page }) => {
    await page.goto('/login');
    
    await page.getByTestId('signup-link').click();
    
    await expect(page).toHaveURL('/signup');
    await expect(page.getByTestId('signup-form')).toBeVisible();
  });

  test('should signup with valid data', async ({ page }) => {
    await page.goto('/signup');
    
    const timestamp = Date.now();
    await page.getByTestId('name-input').fill('Test User');
    await page.getByTestId('email-input').fill(`test${timestamp}@flashit.app`);
    await page.getByTestId('password-input').fill('Test1234!');
    await page.getByTestId('confirm-password-input').fill('Test1234!');
    await page.getByTestId('signup-button').click();
    
    // Should navigate to home or verification page
    await page.waitForURL(/\/(home|verify-email)/, { timeout: 5000 });
  });

  test('should validate password strength', async ({ page }) => {
    await page.goto('/signup');
    
    await page.getByTestId('password-input').fill('weak');
    await page.getByTestId('password-input').blur();
    
    await expect(page.getByText(/at least 8 characters/i)).toBeVisible();
  });

  test('should validate password confirmation', async ({ page }) => {
    await page.goto('/signup');
    
    await page.getByTestId('password-input').fill('Test1234!');
    await page.getByTestId('confirm-password-input').fill('Different123!');
    await page.getByTestId('signup-button').click();
    
    await expect(page.getByText(/passwords do not match/i)).toBeVisible();
  });

  test('should navigate to forgot password page', async ({ page }) => {
    await page.goto('/login');
    
    await page.getByTestId('forgot-password-link').click();
    
    await expect(page).toHaveURL('/forgot-password');
    await expect(page.getByTestId('forgot-password-form')).toBeVisible();
  });

  test('should request password reset', async ({ page }) => {
    await page.goto('/forgot-password');
    
    await page.getByTestId('email-input').fill('test@flashit.app');
    await page.getByTestId('reset-button').click();
    
    await expect(page.getByText(/reset link sent/i)).toBeVisible({ timeout: 5000 });
  });

  test('should logout', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('user-menu').click();
    await authenticatedPage.getByTestId('logout-button').click();
    
    // Should redirect to login
    await expect(authenticatedPage).toHaveURL('/login', { timeout: 3000 });
  });

  test('should persist login session', async ({ page, testUser }) => {
    await page.goto('/login');
    
    // Login
    await page.getByTestId('email-input').fill(testUser.email);
    await page.getByTestId('password-input').fill(testUser.password);
    await page.getByTestId('login-button').click();
    await page.waitForURL('/');
    
    // Reload page
    await page.reload();
    
    // Should still be logged in
    await expect(page.getByTestId('home-screen')).toBeVisible();
  });

  test('should show password visibility toggle', async ({ page }) => {
    await page.goto('/login');
    
    const passwordInput = page.getByTestId('password-input');
    await passwordInput.fill('Test1234!');
    
    // Password should be hidden
    await expect(passwordInput).toHaveAttribute('type', 'password');
    
    // Toggle visibility
    await page.getByTestId('toggle-password-visibility').click();
    await expect(passwordInput).toHaveAttribute('type', 'text');
  });

  test('should support social login options', async ({ page }) => {
    await page.goto('/login');
    
    await expect(page.getByTestId('google-login-button')).toBeVisible();
    await expect(page.getByTestId('github-login-button')).toBeVisible();
  });
});
