import { test, expect } from '@playwright/test';

test.describe('PHR visual regression @visual', () => {
  test('login screen matches baseline', async ({ page }) => {
    await page.goto('/login');
    await expect(page).toHaveScreenshot('phr-login.png');
  });

  test('dashboard screen matches baseline', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await expect(page).toHaveScreenshot('phr-dashboard.png');
  });
});
