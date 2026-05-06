/**
 * E2E Test: Accessibility (Web)
 * Tests keyboard navigation and screen reader support
 */

import { test, expect } from './fixtures';

test.describe('FlashIt accessibility @a11y', () => {
  test('login form exposes labels and announces errors', async ({ page }) => {
    await page.route('**/auth/login', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Invalid credentials', message: 'Invalid email or password' }),
      });
    });

    await page.goto('/login');
    await expect(page.getByLabel('Email')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();
    await page.getByLabel('Email').fill('wrong@email.com');
    await page.getByLabel('Password').fill('wrongpass');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByRole('alert')).toHaveAttribute('aria-live', 'polite');
  });

  test('shell exposes skip link, landmarks, and keyboard focus', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('flashit_token', 'flashit-a11y-token');
    });
    await page.route('**/auth/me', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          user: {
            id: 'user-member',
            email: 'member@flashit.app',
            displayName: 'Member User',
            createdAt: '2026-05-05T00:00:00Z',
            updatedAt: '2026-05-05T00:00:00Z',
            tier: 'FREE',
          },
        }),
      });
    });

    await page.goto('/settings');
    await page.keyboard.press('Tab');
    await expect(page.getByText('Skip to main content')).toBeFocused();
    await expect(page.getByRole('navigation')).toBeVisible();
    await expect(page.getByRole('main')).toBeVisible();
  });

  test('premium denial route stays readable and keyboard accessible', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('flashit_token', 'flashit-denied-token');
    });
    await page.route('**/auth/me', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          user: {
            id: 'user-free',
            email: 'member@flashit.app',
            displayName: 'Free Member',
            createdAt: '2026-05-05T00:00:00Z',
            updatedAt: '2026-05-05T00:00:00Z',
            tier: 'FREE',
          },
        }),
      });
    });

    await page.goto('/analytics');
    await expect(page.getByTestId('flashit-access-denied')).toBeVisible();
    await expect(page.getByText('Permission denied')).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
  });

  test('settings keeps sensitive billing and privacy controls labelled', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('flashit_token', 'flashit-settings-token');
    });
    await page.route('**/auth/me', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          user: {
            id: 'user-pro',
            email: 'premium@flashit.app',
            displayName: 'Premium User',
            createdAt: '2026-05-05T00:00:00Z',
            updatedAt: '2026-05-05T00:00:00Z',
            tier: 'PRO',
          },
        }),
      });
    });

    await page.goto('/settings');
    await expect(page.getByRole('button', { name: 'Billing' })).toBeVisible();
    await page.getByRole('button', { name: 'Billing' }).click();
    await expect(page.getByText('Subscription')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Upgrade' }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Privacy' }).click();
    await expect(page.getByText('Retention period depends on your subscription tier.')).toBeVisible();
  });
});
