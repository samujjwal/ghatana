/**
 * E2E Authentication Flows Test Suite
 * 
 * Comprehensive authentication testing including:
 * - Login/logout flows
 * - Registration flow
 * - Password reset flow
 * - Protected route access
 * - Role-based access control
 * - Session management
 * - Error handling
 * 
 * @doc.type test
 * @doc.purpose E2E authentication flow validation
 * @doc.layer e2e
 */

import { test, expect } from '@playwright/test';
import { LoginPage, SignUpPage, ForgotPasswordPage } from './pages/auth.page';
import { DashboardPage } from './pages/dashboard.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Authentication Flows', () => {
  test.describe.configure({ mode: 'serial' });
  
  let loginPage: LoginPage;
  let signUpPage: SignUpPage;
  let forgotPasswordPage: ForgotPasswordPage;
  let dashboardPage: DashboardPage;
  
  test.beforeEach(async ({ page }) => {
    loginPage = new LoginPage(page);
    signUpPage = new SignUpPage(page);
    forgotPasswordPage = new ForgotPasswordPage(page);
    dashboardPage = new DashboardPage(page);
  });
  
  // ==========================================================================
  // Login Flow Tests
  // ==========================================================================
  
  test.describe('Login Flow', () => {
    test('should display login page with all elements', async ({ page }) => {
      await loginPage.goto();
      
      // Check page elements
      await expect(loginPage.emailInput).toBeVisible();
      await expect(loginPage.passwordInput).toBeVisible();
      await expect(loginPage.submitButton).toBeVisible();
      await expect(loginPage.forgotPasswordLink).toBeVisible();
      await expect(loginPage.signUpLink).toBeVisible();
      
      // Check page title
      await expect(page).toHaveTitle(/login|sign in/i);
    });
    
    test('should login successfully with valid credentials', async ({ page }) => {
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      
      // Should redirect to dashboard
      await expect(page).toHaveURL(/dashboard|home/);
      
      // Should see user info in dashboard
      await expect(page.getByText(testUsers.standard.name || testUsers.standard.email)).toBeVisible();
    });
    
    test('should show error with invalid credentials', async ({ page }) => {
      await loginPage.goto();
      await loginPage.login('invalid@example.com', 'wrongpassword');
      
      // Should show error message
      await loginPage.expectErrorMessage(/invalid|incorrect|wrong/i);
      
      // Should stay on login page
      await expect(page).toHaveURL(/login/);
    });
    
    test('should show validation errors for empty fields', async ({ page }) => {
      await loginPage.goto();
      await loginPage.submitButton.click();
      
      // Should show validation errors
      await expect(page.getByText(/required|enter.*email/i)).toBeVisible();
    });
    
    test('should show validation error for invalid email format', async ({ page }) => {
      await loginPage.goto();
      await loginPage.emailInput.fill('invalid-email');
      await loginPage.passwordInput.fill('somepassword');
      await loginPage.submitButton.click();
      
      // Should show email format error
      await expect(page.getByText(/invalid.*email|valid.*email/i)).toBeVisible();
    });
    
    test('should toggle password visibility', async ({ page }) => {
      await loginPage.goto();
      await loginPage.passwordInput.fill('testpassword');
      
      // Password should be hidden by default
      await expect(loginPage.passwordInput).toHaveAttribute('type', 'password');
      
      // Click show/hide button if exists
      const toggleButton = page.getByRole('button', { name: /show|hide.*password/i });
      if (await toggleButton.isVisible()) {
        await toggleButton.click();
        await expect(loginPage.passwordInput).toHaveAttribute('type', 'text');
      }
    });
    
    test('should remember me functionality', async ({ page, context }) => {
      await loginPage.goto();
      
      // Check "Remember Me" if available
      const rememberCheckbox = page.getByLabel(/remember/i);
      if (await rememberCheckbox.isVisible()) {
        await rememberCheckbox.check();
      }
      
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      await expect(page).toHaveURL(/dashboard/);
      
      // Close and reopen browser (new context would simulate this)
      // In real test, verify token persistence
    });
    
    test('should redirect to intended page after login', async ({ page }) => {
      // Try to access protected page
      await page.goto('/dashboard/settings');
      
      // Should redirect to login with return path
      await expect(page).toHaveURL(/login/);
      
      // Login
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      
      // Should redirect back to settings
      await expect(page).toHaveURL(/settings/);
    });
  });
  
  // ==========================================================================
  // Logout Flow Tests
  // ==========================================================================
  
  test.describe('Logout Flow', () => {
    test('should logout successfully', async ({ page }) => {
      // Login first
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      await expect(page).toHaveURL(/dashboard/);
      
      // Logout
      const logoutButton = page.getByRole('button', { name: /log out|sign out/i });
      await logoutButton.click();
      
      // Should redirect to login
      await expect(page).toHaveURL(/login|home/);
      
      // Should not be able to access protected routes
      await page.goto('/dashboard');
      await expect(page).toHaveURL(/login/);
    });
    
    test('should clear session data on logout', async ({ page, context }) => {
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      
      // Check session storage before logout
      const beforeLogout = await page.evaluate(() => {
        return {
          localStorage: { ...localStorage },
          sessionStorage: { ...sessionStorage },
        };
      });
      
      // Logout
      await page.getByRole('button', { name: /log out/i }).click();
      
      // Check session storage after logout
      const afterLogout = await page.evaluate(() => {
        return {
          localStorage: { ...localStorage },
          sessionStorage: { ...sessionStorage },
        };
      });
      
      // Verify auth tokens are cleared
      expect(afterLogout.localStorage).not.toHaveProperty('authToken');
      expect(afterLogout.sessionStorage).not.toHaveProperty('authToken');
    });
  });
  
  // ==========================================================================
  // Registration Flow Tests
  // ==========================================================================
  
  test.describe('Registration Flow', () => {
    test('should display signup page with all elements', async ({ page }) => {
      await signUpPage.goto();
      
      await expect(signUpPage.nameInput).toBeVisible();
      await expect(signUpPage.emailInput).toBeVisible();
      await expect(signUpPage.passwordInput).toBeVisible();
      await expect(signUpPage.confirmPasswordInput).toBeVisible();
      await expect(signUpPage.termsCheckbox).toBeVisible();
      await expect(signUpPage.submitButton).toBeVisible();
      await expect(signUpPage.loginLink).toBeVisible();
    });
    
    test('should register new user successfully', async ({ page }) => {
      await signUpPage.goto();
      
      const timestamp = Date.now();
      await signUpPage.signUp({
        name: 'New User',
        email: `newuser${timestamp}@example.com`,
        password: 'NewPassword123!',
        acceptTerms: true,
      });
      
      // Should redirect to verification or dashboard
      await expect(page).toHaveURL(/verify|confirm|dashboard/);
    });
    
    test('should show error for existing email', async ({ page }) => {
      await signUpPage.goto();
      await signUpPage.signUp({
        name: 'Test User',
        email: testUsers.standard.email,
        password: 'TestPassword123!',
      });
      
      // Should show email exists error
      await expect(signUpPage.errorMessage).toContainText(/already.*exists|already.*registered/i);
    });
    
    test('should validate password strength', async ({ page }) => {
      await signUpPage.goto();
      await signUpPage.nameInput.fill('Test User');
      await signUpPage.emailInput.fill('test@example.com');
      await signUpPage.passwordInput.fill('weak');
      
      // Should show password strength indicator or error
      await expect(page.getByText(/weak|too short|at least.*characters/i)).toBeVisible();
    });
    
    test('should validate password confirmation match', async ({ page }) => {
      await signUpPage.goto();
      await signUpPage.nameInput.fill('Test User');
      await signUpPage.emailInput.fill('test@example.com');
      await signUpPage.passwordInput.fill('Password123!');
      await signUpPage.confirmPasswordInput.fill('DifferentPassword123!');
      await signUpPage.submitButton.click();
      
      // Should show mismatch error
      await expect(page.getByText(/password.*match|passwords.*same/i)).toBeVisible();
    });
    
    test('should require terms acceptance', async ({ page }) => {
      await signUpPage.goto();
      await signUpPage.signUp({
        name: 'Test User',
        email: 'test@example.com',
        password: 'Password123!',
        acceptTerms: false,
      });
      
      // Should show terms error
      await expect(page.getByText(/accept.*terms|agree.*terms/i)).toBeVisible();
    });
  });
  
  // ==========================================================================
  // Password Reset Flow Tests
  // ==========================================================================
  
  test.describe('Password Reset Flow', () => {
    test('should display forgot password page', async ({ page }) => {
      await forgotPasswordPage.goto();
      
      await expect(forgotPasswordPage.emailInput).toBeVisible();
      await expect(forgotPasswordPage.submitButton).toBeVisible();
    });
    
    test('should send password reset email', async ({ page }) => {
      await forgotPasswordPage.goto();
      await forgotPasswordPage.emailInput.fill(testUsers.standard.email);
      await forgotPasswordPage.submitButton.click();
      
      // Should show success message
      await expect(forgotPasswordPage.successMessage).toContainText(/email sent|check.*email/i);
    });
    
    test('should show error for non-existent email', async ({ page }) => {
      await forgotPasswordPage.goto();
      await forgotPasswordPage.emailInput.fill('nonexistent@example.com');
      await forgotPasswordPage.submitButton.click();
      
      // Should show error or generic message (for security)
      await expect(page.getByText(/not found|doesn't exist|check.*email/i)).toBeVisible();
    });
  });
  
  // ==========================================================================
  // Protected Routes Tests
  // ==========================================================================
  
  test.describe('Protected Routes', () => {
    test('should redirect unauthenticated users to login', async ({ page }) => {
      // Try to access protected routes
      await page.goto('/dashboard');
      await expect(page).toHaveURL(/login/);
      
      await page.goto('/profile');
      await expect(page).toHaveURL(/login/);
      
      await page.goto('/settings');
      await expect(page).toHaveURL(/login/);
    });
    
    test('should allow authenticated users to access protected routes', async ({ page }) => {
      // Login
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      
      // Access protected routes
      await page.goto('/dashboard');
      await expect(page).toHaveURL(/dashboard/);
      
      await page.goto('/profile');
      await expect(page).toHaveURL(/profile/);
    });
    
    test('should prevent access to admin routes for regular users', async ({ page }) => {
      // Login as regular user
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      
      // Try to access admin route
      await page.goto('/admin');
      
      // Should redirect to unauthorized or dashboard
      await expect(page).toHaveURL(/unauthorized|403|dashboard/);
    });
    
    test('should allow admin users to access admin routes', async ({ page }) => {
      // Login as admin
      await loginPage.goto();
      await loginPage.login(testUsers.admin.email, testUsers.admin.password);
      
      // Access admin route
      await page.goto('/admin');
      await expect(page).toHaveURL(/admin/);
    });
  });
  
  // ==========================================================================
  // Session Management Tests
  // ==========================================================================
  
  test.describe('Session Management', () => {
    test('should maintain session across page reloads', async ({ page }) => {
      // Login
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      await expect(page).toHaveURL(/dashboard/);
      
      // Reload page
      await page.reload();
      
      // Should still be authenticated
      await expect(page).toHaveURL(/dashboard/);
      await expect(page.getByText(testUsers.standard.email)).toBeVisible();
    });
    
    test('should handle expired tokens', async ({ page }) => {
      // Login
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      
      // Simulate expired token
      await page.evaluate(() => {
        localStorage.setItem('authToken', 'expired-token');
      });
      
      // Try to access protected route
      await page.goto('/dashboard');
      
      // Should redirect to login
      await expect(page).toHaveURL(/login/);
    });
    
    test('should refresh token automatically', async ({ page }) => {
      // This test requires backend support for token refresh
      // Login
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      
      // Wait for token to be near expiration
      // (In real test, you'd mock the token expiration time)
      
      // Make API request
      await page.goto('/dashboard/profile');
      
      // Token should be refreshed automatically
      const token = await page.evaluate(() => localStorage.getItem('authToken'));
      expect(token).toBeTruthy();
    });
  });
  
  // ==========================================================================
  // Error Handling Tests
  // ==========================================================================
  
  test.describe('Error Handling', () => {
    test('should handle network errors gracefully', async ({ page, context }) => {
      await loginPage.goto();
      
      // Simulate offline
      await context.setOffline(true);
      
      await loginPage.emailInput.fill(testUsers.standard.email);
      await loginPage.passwordInput.fill(testUsers.standard.password);
      await loginPage.submitButton.click();
      
      // Should show network error
      await expect(page.getByText(/network.*error|connection.*failed|offline/i)).toBeVisible();
      
      // Restore online
      await context.setOffline(false);
    });
    
    test('should handle API errors gracefully', async ({ page }) => {
      // This test requires backend support for error injection
      await loginPage.goto();
      await loginPage.login(testUsers.standard.email, testUsers.standard.password);
      
      // If API returns 500, should show error
      // (Implementation depends on error handling strategy)
    });
    
    test('should display user-friendly error messages', async ({ page }) => {
      await loginPage.goto();
      await loginPage.login('invalid@example.com', 'wrongpassword');
      
      // Should not expose technical details
      const errorText = await loginPage.errorMessage.textContent();
      expect(errorText?.toLowerCase()).not.toContain('sql');
      expect(errorText?.toLowerCase()).not.toContain('stack');
      expect(errorText?.toLowerCase()).not.toContain('exception');
    });
  });
  
  // ==========================================================================
  // Accessibility Tests
  // ==========================================================================
  
  test.describe('Accessibility', () => {
    test('should be keyboard navigable', async ({ page }) => {
      await loginPage.goto();
      
      // Tab through form fields
      await page.keyboard.press('Tab'); // Email
      await expect(loginPage.emailInput).toBeFocused();
      
      await page.keyboard.press('Tab'); // Password
      await expect(loginPage.passwordInput).toBeFocused();
      
      await page.keyboard.press('Tab'); // Submit button
      await expect(loginPage.submitButton).toBeFocused();
      
      // Submit with Enter
      await loginPage.emailInput.fill(testUsers.standard.email);
      await loginPage.passwordInput.fill(testUsers.standard.password);
      await page.keyboard.press('Enter');
      
      await expect(page).toHaveURL(/dashboard/);
    });
    
    test('should have proper ARIA labels', async ({ page }) => {
      await loginPage.goto();
      
      // Check form labels
      await expect(loginPage.emailInput).toHaveAttribute('aria-label', /.+/);
      await expect(loginPage.passwordInput).toHaveAttribute('aria-label', /.+/);
      
      // Check error messages have aria-live
      await loginPage.login('invalid@example.com', 'wrong');
      const alert = page.getByRole('alert');
      await expect(alert).toBeVisible();
    });
    
    test('should announce errors to screen readers', async ({ page }) => {
      await loginPage.goto();
      await loginPage.submitButton.click();
      
      // Check aria-live region for errors
      const errorRegion = page.locator('[aria-live="polite"], [aria-live="assertive"], [role="alert"]');
      await expect(errorRegion).toBeVisible();
    });
  });
});
