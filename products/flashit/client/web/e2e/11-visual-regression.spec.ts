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
});
