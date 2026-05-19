import { expect, test } from '@playwright/test';
import { mockAllAPIs } from './helpers/api-mocks';

test.use({ browserName: 'chromium' });

test.describe('Accessibility contracts', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllAPIs(page);
  });

  test('@a11y home surface exposes stable landmarks and keyboard navigation', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('main')).toBeVisible();
    await expect(page.getByRole('navigation')).toBeVisible();

    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
  });

  test('@a11y data surface keeps collection controls nameable', async ({ page }) => {
    await page.goto('/data');

    await expect(page.getByRole('main')).toBeVisible();
    await expect(page.getByRole('heading', { name: /collections|data/i })).toBeVisible();
    await expect(page.getByRole('button').first()).toBeVisible();
  });
});
