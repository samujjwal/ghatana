/**
 * E2E: Login page user journeys.
 */
import { test, expect } from '@playwright/test';
import { loginAs, mockDmosApi, TEST_WORKSPACE } from './fixtures';

test.describe('login', () => {
  test('renders the login form', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible();
    await expect(page.locator('[data-testid="login-token"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-workspace-id"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-tenant-id"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-principal-id"]')).toBeVisible();
    await expect(page.locator('[data-testid="login-submit"]')).toBeVisible();
  });

  test('shows validation error when fields are empty', async ({ page }) => {
    await page.goto('/login');
    await page.click('[data-testid="login-submit"]');
    await expect(page.getByRole('alert')).toContainText(/required/i);
  });

  test('redirects to dashboard after successful login', async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page);
    await expect(page).toHaveURL(
      new RegExp(`/workspaces/${TEST_WORKSPACE}/dashboard`),
    );
    await expect(page.locator('[data-testid="dashboard-page"]')).toBeVisible();
  });

  test('redirects unauthenticated access to /login', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/dashboard`);
    await expect(page).toHaveURL(/\/login/);
  });
});
