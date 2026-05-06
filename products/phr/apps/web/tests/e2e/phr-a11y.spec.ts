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

  test('dashboard keeps landmark and keyboard navigation visible', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await expect(page.getByText('Patient summary')).toBeVisible();
    await expect(page.getByRole('main')).toBeVisible();
    await expect(page.getByRole('navigation')).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
  });

  test('clinician emergency workflow remains keyboard accessible', async ({ page }) => {
    await page.goto('/login');
    await page.evaluate(() => {
      window.localStorage.setItem('phr.currentRole', 'clinician');
    });
    await page.goto('/emergency');
    await expect(page.getByText('Break-glass workflow')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Request emergency access' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Notify caregiver' })).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
  });

  test('patient emergency denial stays visible and readable', async ({ page }) => {
    await page.goto('/login');
    await page.evaluate(() => {
      window.localStorage.setItem('phr.currentRole', 'patient');
    });
    await page.goto('/emergency');
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText('Permission denied')).toBeVisible();
    await expect(page.getByText(/not available for the current persona/i)).toBeVisible();
  });
});
