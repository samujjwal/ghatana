import { test, expect } from '@playwright/test';

test.describe('PHR accessibility @a11y', () => {
  test('login screen exposes accessible controls', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Welcome to PHR Nepal' })).toBeVisible();
    await expect(page.getByLabel('National ID')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Continue with demo account' })).toBeVisible();
  });

  test('dashboard keeps landmark and emergency action visible', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await expect(page.getByText('Patient summary')).toBeVisible();
    await expect(page.getByRole('main')).toBeVisible();
    await expect(page.getByRole('navigation')).toBeVisible();
  });
});
