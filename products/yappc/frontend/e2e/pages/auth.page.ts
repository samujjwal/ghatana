/**
 * E2E Page Objects - Authentication Pages
 *
 * @description Page object model for authentication-related pages.
 */

import { Page, Locator, expect } from '@playwright/test';

export class LoginPage {
  readonly page: Page;
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;
  readonly errorMessage: Locator;
  readonly forgotPasswordLink: Locator;
  readonly signUpLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.emailInput = page.getByLabel(/email/i);
    this.passwordInput = page.getByLabel(/password/i);
    this.submitButton = page.getByRole('button', { name: /sign in|log in/i });
    this.errorMessage = page.getByRole('alert');
    this.forgotPasswordLink = page.getByRole('link', { name: /forgot password/i });
    this.signUpLink = page.getByRole('link', { name: /sign up|register/i });
  }

  async goto() {
    await this.page.goto('/login');
  }

  async login(email: string, password: string) {
    await this.emailInput.fill(email);
    await this.passwordInput.fill(password);
    await this.submitButton.click();
  }

  async expectErrorMessage(message: string | RegExp) {
    await expect(this.errorMessage).toContainText(message);
  }

  async expectLoginSuccess() {
    await expect(this.page).toHaveURL(/dashboard|projects/);
  }
}

export class SignUpPage {
  readonly page: Page;
  readonly nameInput: Locator;
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly confirmPasswordInput: Locator;
  readonly termsCheckbox: Locator;
  readonly submitButton: Locator;
  readonly errorMessage: Locator;
  readonly loginLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.nameInput = page.getByLabel(/name/i);
    this.emailInput = page.getByLabel(/email/i);
    this.passwordInput = page.getByLabel(/^password$/i);
    this.confirmPasswordInput = page.getByLabel(/confirm password/i);
    this.termsCheckbox = page.getByLabel(/terms|agree/i);
    this.submitButton = page.getByRole('button', { name: /sign up|create account/i });
    this.errorMessage = page.getByRole('alert');
    this.loginLink = page.getByRole('link', { name: /log in|sign in/i });
  }

  async goto() {
    await this.page.goto('/signup');
  }

  async signUp(data: {
    name: string;
    email: string;
    password: string;
    acceptTerms?: boolean;
  }) {
    await this.nameInput.fill(data.name);
    await this.emailInput.fill(data.email);
    await this.passwordInput.fill(data.password);
    await this.confirmPasswordInput.fill(data.password);
    if (data.acceptTerms !== false) {
      await this.termsCheckbox.check();
    }
    await this.submitButton.click();
  }

  async expectSignUpSuccess() {
    await expect(this.page).toHaveURL(/verify|confirm|dashboard/);
  }
}

export class ForgotPasswordPage {
  readonly page: Page;
  readonly emailInput: Locator;
  readonly submitButton: Locator;
  readonly successMessage: Locator;
  readonly errorMessage: Locator;
  readonly backToLoginLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.emailInput = page.getByLabel(/email/i);
    this.submitButton = page.getByRole('button', { name: /reset|send/i });
    this.successMessage = page.getByText(/email sent|check your inbox/i);
    this.errorMessage = page.getByRole('alert');
    this.backToLoginLink = page.getByRole('link', { name: /back to login/i });
  }

  async goto() {
    await this.page.goto('/forgot-password');
  }

  async requestReset(email: string) {
    await this.emailInput.fill(email);
    await this.submitButton.click();
  }

  async expectResetEmailSent() {
    await expect(this.successMessage).toBeVisible();
  }
}

/**
 * Reset Password Page
 */
export class ResetPasswordPage {
  readonly page: Page;
  readonly passwordInput: Locator;
  readonly confirmPasswordInput: Locator;
  readonly submitButton: Locator;
  readonly successMessage: Locator;
  readonly errorMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.passwordInput = page.getByLabel(/^new password|password$/i).first();
    this.confirmPasswordInput = page.getByLabel(/confirm password/i);
    this.submitButton = page.getByRole('button', { name: /reset|change password/i });
    this.successMessage = page.getByText(/password.*changed|password.*reset/i);
    this.errorMessage = page.getByRole('alert');
  }

  async goto(token: string) {
    await this.page.goto(`/reset-password?token=${token}`);
  }

  async resetPassword(password: string) {
    await this.passwordInput.fill(password);
    await this.confirmPasswordInput.fill(password);
    await this.submitButton.click();
  }

  async expectPasswordResetSuccess() {
    await expect(this.successMessage).toBeVisible();
  }
}

/**
 * Auth helpers for common authentication flows
 */
export class AuthHelpers {
  constructor(private page: Page) {}

  /**
   * Complete login flow
   */
  async login(email: string, password: string): Promise<void> {
    const loginPage = new LoginPage(this.page);
    await loginPage.goto();
    await loginPage.login(email, password);
    await loginPage.expectLoginSuccess();
  }

  /**
   * Complete registration flow
   */
  async register(data: {
    name: string;
    email: string;
    password: string;
  }): Promise<void> {
    const signUpPage = new SignUpPage(this.page);
    await signUpPage.goto();
    await signUpPage.signUp(data);
    await signUpPage.expectSignUpSuccess();
  }

  /**
   * Complete logout flow
   */
  async logout(): Promise<void> {
    const logoutButton = this.page.getByRole('button', { name: /log out|sign out/i });
    await logoutButton.click();
    await expect(this.page).toHaveURL(/login|home/);
  }

  /**
   * Check if user is authenticated
   */
  async isAuthenticated(): Promise<boolean> {
    const token = await this.page.evaluate(() => {
      return localStorage.getItem('authToken') || sessionStorage.getItem('authToken');
    });
    return !!token;
  }

  /**
   * Clear authentication data
   */
  async clearAuth(): Promise<void> {
    await this.page.evaluate(() => {
      localStorage.removeItem('authToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('user');
      sessionStorage.removeItem('authToken');
      sessionStorage.removeItem('refreshToken');
      sessionStorage.removeItem('user');
    });
  }

  /**
   * Set authentication token
   */
  async setAuthToken(token: string, storage: 'local' | 'session' = 'local'): Promise<void> {
    await this.page.evaluate(
      ({ token, storage }) => {
        const storageObj = storage === 'local' ? localStorage : sessionStorage;
        storageObj.setItem('authToken', token);
      },
      { token, storage }
    );
  }

  /**
   * Get authentication token
   */
  async getAuthToken(storage: 'local' | 'session' = 'local'): Promise<string | null> {
    return await this.page.evaluate((storage) => {
      const storageObj = storage === 'local' ? localStorage : sessionStorage;
      return storageObj.getItem('authToken');
    }, storage);
  }

  /**
   * Set user data
   */
  async setUser(user: unknown, storage: 'local' | 'session' = 'local'): Promise<void> {
    await this.page.evaluate(
      ({ user, storage }) => {
        const storageObj = storage === 'local' ? localStorage : sessionStorage;
        storageObj.setItem('user', JSON.stringify(user));
      },
      { user, storage }
    );
  }

  /**
   * Get user data
   */
  async getUser(storage: 'local' | 'session' = 'local'): Promise<any | null> {
    return await this.page.evaluate((storage) => {
      const storageObj = storage === 'local' ? localStorage : sessionStorage;
      const userData = storageObj.getItem('user');
      return userData ? JSON.parse(userData) : null;
    }, storage);
  }

  /**
   * Wait for authentication state
   */
  async waitForAuth(timeout: number = 5000): Promise<void> {
    await this.page.waitForFunction(
      () => {
        return !!(localStorage.getItem('authToken') || sessionStorage.getItem('authToken'));
      },
      { timeout }
    );
  }

  /**
   * Expect to be on login page
   */
  async expectLoginPage(): Promise<void> {
    await expect(this.page).toHaveURL(/login/);
  }

  /**
   * Expect to be authenticated
   */
  async expectAuthenticated(): Promise<void> {
    const isAuth = await this.isAuthenticated();
    expect(isAuth).toBeTruthy();
  }

  /**
   * Expect to be unauthenticated
   */
  async expectUnauthenticated(): Promise<void> {
    const isAuth = await this.isAuthenticated();
    expect(isAuth).toBeFalsy();
  }

  /**
   * Mock backend authentication success
   */
  async mockAuthSuccess(user: any = {}, token: string = 'mock-token'): Promise<void> {
    await this.page.route('**/api/auth/login', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          user: {
            id: user.id || '1',
            email: user.email || 'test@example.com',
            name: user.name || 'Test User',
            ...user,
          },
          token,
        }),
      });
    });
  }

  /**
   * Mock backend authentication failure
   */
  async mockAuthFailure(message: string = 'Invalid credentials', status: number = 401): Promise<void> {
    await this.page.route('**/api/auth/login', (route) => {
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify({
          error: message,
        }),
      });
    });
  }

  /**
   * Mock backend registration success
   */
  async mockRegisterSuccess(user: any = {}): Promise<void> {
    await this.page.route('**/api/auth/register', (route) => {
      route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          user: {
            id: user.id || '1',
            email: user.email || 'newuser@example.com',
            name: user.name || 'New User',
            ...user,
          },
          message: 'Registration successful',
        }),
      });
    });
  }

  /**
   * Mock token refresh
   */
  async mockTokenRefresh(newToken: string = 'new-mock-token'): Promise<void> {
    await this.page.route('**/api/auth/refresh', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: newToken,
        }),
      });
    });
  }
}
