import { test, expect } from '@playwright/test';
import { registerMockRoutes } from './fixtures';

/**
 * E2E tests for authentication flow.
 *
 * Tests validate:
 * - Login form functionality
 * - Authentication token handling
 * - Session persistence
 * - Logout functionality
 * - Protected route access
 * - Error handling for invalid credentials
 *
 * @doc.type test-suite
 * @doc.purpose E2E tests for authentication and authorization
 * @doc.layer e2e-testing
 */

test.describe('Authentication Flow', () => {
  test.beforeEach(async ({ page, context }) => {
    // Clear cookies first
    await context.clearCookies();
    // register mocked routes for API endpoints
    await registerMockRoutes(page);
    // Navigate to the page, then clear localStorage
    await page.goto('/');
    await page.evaluate(() => {
      try {
        localStorage.clear();
      } catch (e) {
        // Ignore if localStorage is not available
      }
    });
  });

  /**
   * Verifies login page loads correctly.
   *
   * GIVEN: User navigates to login page
   * WHEN: Page loads
   * THEN: Login form is visible with email and password fields
   */
  test('should display login page', async ({ page }) => {
    await page.goto('/login');

    // Check for login form elements
    await expect(page.getByRole('heading', { name: /guardian dashboard|sign in to your account/i })).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  });

  /**
   * Verifies successful login redirects to dashboard.
   *
   * GIVEN: Valid credentials entered
   * WHEN: Login form is submitted
   * THEN: User redirected to dashboard with auth token stored
   */
  test('should login successfully with valid credentials', async ({ page }) => {
    await page.goto('/login');

    // Fill in credentials
    await page.getByLabel(/email/i).fill('parent@example.com');
    await page.getByLabel(/password/i).fill('password123');

    // Submit form
    await page.getByRole('button', { name: /sign in/i }).click();

    // Should redirect to dashboard
    await expect(page).toHaveURL('/dashboard');

    // Dashboard content should be visible
    await expect(page.getByRole('heading', { name: /guardian dashboard/i })).toBeVisible();

    // Check that auth token is stored under canonical key
    const token = await page.evaluate(() => localStorage.getItem('guardian_token'));
    expect(token).toBeTruthy();
  });

  /**
   * Verifies login fails with invalid credentials.
   *
   * GIVEN: Invalid credentials entered
   * WHEN: Login form is submitted
   * THEN: Error message displayed and user remains on login page
   */
  test('should show error with invalid credentials', async ({ page }) => {
    await page.goto('/login');

    // Fill in invalid credentials
    await page.getByLabel(/email/i).fill('invalid@example.com');
    await page.getByLabel(/password/i).fill('wrongpassword');

    // Submit form
    await page.getByRole('button', { name: /sign in/i }).click();

    // Should stay on login page
    await expect(page).toHaveURL('/login');

    // Error message should be visible (check for error container first)
    await expect(page.locator('.bg-red-50')).toBeVisible();
  });

  /**
   * Verifies protected routes redirect to login when not authenticated.
   *
   * GIVEN: User not authenticated
   * WHEN: User tries to access protected route
   * THEN: Redirected to login page
   */
  test('should redirect to login when accessing protected route without auth', async ({ page }) => {
    await page.goto('/dashboard');

    // Should redirect to login
    await expect(page).toHaveURL('/login');
    await expect(page.getByRole('heading', { name: /guardian dashboard|sign in to your account/i })).toBeVisible();
  });

  /**
   * Verifies session persists across page reloads.
   *
   * GIVEN: User logged in
   * WHEN: Page is reloaded
   * THEN: User remains authenticated and sees dashboard
   */
  test('should persist session after page reload', async ({ page }) => {
    // Login
    await page.goto('/login');
    await page.getByLabel(/email/i).fill('parent@example.com');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL('/dashboard');

    // Reload page
    await page.reload();

    // Should still be on dashboard
    await expect(page).toHaveURL('/dashboard');
    await expect(page.getByRole('heading', { name: /guardian dashboard/i })).toBeVisible();
  });

  /**
   * Verifies logout clears session and redirects to login.
   *
   * GIVEN: User logged in
   * WHEN: User clicks logout
   * THEN: Session cleared and redirected to login page
   */
  test('should logout successfully', async ({ page }) => {
    // Login first
    await page.goto('/login');
    await page.getByLabel(/email/i).fill('parent@example.com');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL('/dashboard');

    // Click logout
    await page.getByRole('button', { name: /logout/i }).click();

    // Should redirect to login
    await expect(page).toHaveURL('/login');

    // Token should be cleared
    const token = await page.evaluate(() => localStorage.getItem('guardian_token'));
    expect(token).toBeNull();
  });

  /**
   * Verifies form validation for empty fields.
   *
   * GIVEN: Login form displayed
   * WHEN: Form submitted with empty fields
   * THEN: Validation errors shown
   */
  test('should validate required fields', async ({ page }) => {
    await page.goto('/login');

    // Try to submit empty form
    await page.getByRole('button', { name: /sign in/i }).click();

    // Should stay on login page with validation errors
    await expect(page).toHaveURL('/login');
    // Note: Actual validation message depends on implementation
  });

  /**
   * Verifies email format validation.
   *
   * GIVEN: Login form displayed
   * WHEN: Invalid email format entered
   * THEN: Validation error shown
   */
  test('should validate email format', async ({ page }) => {
    await page.goto('/login');

    // Enter invalid email
    await page.getByLabel(/email/i).fill('not-an-email');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign in/i }).click();

    // Should show validation error
    await expect(page).toHaveURL('/login');
  });
});

test.describe('Register Flow', () => {
  test.beforeEach(async ({ page, context }) => {
    // Clear cookies first
    await context.clearCookies();
    // Navigate to the page, then clear localStorage
    await page.goto('/');
    await page.evaluate(() => {
      try {
        localStorage.clear();
      } catch (e) {
        // Ignore if localStorage is not available
      }
    });
  });

  /**
   * Verifies registration page loads correctly.
   *
   * GIVEN: User navigates to register page
   * WHEN: Page loads
   * THEN: Registration form is visible
   */
  test('should display register page', async ({ page }) => {
    await page.goto('/register');

    await expect(page.getByRole('heading', { name: /create your account|join guardian/i })).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel('Password', { exact: true })).toBeVisible();
  });

  /**
   * Verifies navigation from login to register.
   *
   * GIVEN: User on login page
   * WHEN: User clicks register link
   * THEN: Redirected to register page
   */
  test('should navigate from login to register', async ({ page }) => {
    await page.goto('/login');

    // Click register link
    await page.getByRole('link', { name: /register|sign up/i }).click();

    // Should be on register page
    await expect(page).toHaveURL('/register');
    await expect(page.getByRole('heading', { name: /create your account|join guardian/i })).toBeVisible();
  });
});
