import { test, expect } from './fixtures';

test.describe('FlashIt visual regression @visual', () => {
  test('login screen matches baseline', async ({ page }) => {
    await page.goto('/login');
    await expect(page).toHaveScreenshot('flashit-login.png');
  });

  test('dashboard shell matches baseline', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    await expect(authenticatedPage).toHaveScreenshot('flashit-dashboard.png');
  });

  test('permission denied route matches baseline', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('flashit_token', 'flashit-visual-token');
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
    await expect(page).toHaveScreenshot('flashit-permission-denied.png');
  });

  test('loading state matches baseline', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('flashit_token', 'flashit-loading-token');
    });
    let releaseAuth: (() => void) | null = null;
    const authBarrier = new Promise<void>((resolve) => {
      releaseAuth = resolve;
    });

    await page.route('**/auth/me', async (route) => {
      await authBarrier;
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          user: {
            id: 'user-pro',
            email: 'premium@flashit.app',
            displayName: 'Premium Member',
            createdAt: '2026-05-05T00:00:00Z',
            updatedAt: '2026-05-05T00:00:00Z',
            tier: 'PRO',
          },
        }),
      });
    });

    await page.goto('/analytics', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Loading...')).toBeVisible();
    await expect(page).toHaveScreenshot('flashit-loading-state.png');
    releaseAuth?.();
  });

  test('error state matches baseline', async ({ page }) => {
    await page.route('**/auth/login', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Invalid credentials', message: 'Invalid email or password' }),
      });
    });

    await page.goto('/login');
    await page.getByLabel('Email').fill('wrong@email.com');
    await page.getByLabel('Password').fill('wrongpass');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveScreenshot('flashit-login-error.png');
  });

  test('empty state matches baseline', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('flashit_token', 'flashit-empty-token');
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
    await page.route('**/api/moments**', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          moments: [],
          totalCount: 0,
          nextCursor: null,
        }),
      });
    });

    await page.goto('/moments');
    await expect(page.getByText('No moments found')).toBeVisible();
    await expect(page).toHaveScreenshot('flashit-moments-empty.png');
  });
});
