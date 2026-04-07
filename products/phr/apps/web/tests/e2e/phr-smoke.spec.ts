import { test, expect } from '@playwright/test';

test('loads login page and demo path', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByText('Welcome to PHR Nepal')).toBeVisible();
  await page.getByRole('link', { name: 'Continue with demo account' }).click();
  await expect(page.getByText('Patient summary')).toBeVisible();
});