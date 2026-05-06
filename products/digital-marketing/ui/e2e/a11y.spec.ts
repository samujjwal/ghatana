import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { TEST_WORKSPACE, loginAs, mockDmosApi, navigateInApp } from './fixtures';

test.describe('DMOS accessibility @a11y', () => {
  test('login screen exposes labelled controls', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /DMOS/i })).toBeVisible();
    await expect(page.getByLabel('Bearer Token')).toBeVisible();
    await expect(page.getByLabel('Workspace ID')).toBeVisible();
    await expect(page.getByLabel('Tenant ID')).toBeVisible();
    await expect(page.getByLabel('User / Principal ID')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();
  });

  test('dashboard shell keeps landmarks and passes axe', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await mockDmosApi(page);
    await loginAs(page, { roles: ['marketing-director'] });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);
    await expect(page.getByRole('main')).toBeVisible();
    await expect(page.getByRole('navigation')).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('approval queue exposes alerts and keyboard focus', async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page, { roles: [] });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/approvals`);
    await expect(page.locator('[data-testid="permission-denied-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="permission-denied-banner"]')).toHaveAttribute('role', 'alert');
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
  });

  test('feature unavailable route stays accessible for denied users', async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page, { roles: [] });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/campaigns`);
    await expect(page.locator('[data-testid="feature-unavailable-page"]')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Return to Dashboard' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Go Back' })).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
  });
});
