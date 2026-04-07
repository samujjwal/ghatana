import { Locator, Page, expect } from '@playwright/test';

import {
  loginThroughUi,
  mockFailedLogin,
  mockSuccessfulAuthApis,
  readStoredSession,
  seedStoredSession,
  type MockAuthOptions,
  type StoredSession,
} from '../helpers/auth-journey';

export class AuthPage {
  readonly page: Page;
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;
  readonly errorMessage: Locator;
  readonly sessionExpiredMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.emailInput = page.getByTestId('email-input');
    this.passwordInput = page.getByTestId('password-input');
    this.submitButton = page.getByTestId('login-submit');
    this.errorMessage = page.getByTestId('login-error');
    this.sessionExpiredMessage = page.getByTestId('session-expired-message');
  }

  async goto(query: Record<string, string> = {}): Promise<void> {
    const search = new URLSearchParams(query).toString();
    await this.page.goto(search ? `/login?${search}` : '/login');
  }

  async login(options: MockAuthOptions = {}): Promise<void> {
    await loginThroughUi(this.page, options);
  }

  async mockSuccessfulLogin(options: MockAuthOptions = {}): Promise<void> {
    await mockSuccessfulAuthApis(this.page, options);
  }

  async mockFailedLogin(message?: string): Promise<void> {
    await mockFailedLogin(this.page, message);
  }

  async expectError(message: string): Promise<void> {
    await expect(this.errorMessage).toHaveText(message);
  }

  async expectSessionExpiredBanner(): Promise<void> {
    await expect(this.sessionExpiredMessage).toBeVisible();
  }

  async seedAuthenticatedSession(options: MockAuthOptions = {}): Promise<StoredSession> {
    await mockSuccessfulAuthApis(this.page, options);
    return seedStoredSession(this.page, options);
  }

  async getStoredSession(): Promise<StoredSession | null> {
    return readStoredSession(this.page);
  }
}