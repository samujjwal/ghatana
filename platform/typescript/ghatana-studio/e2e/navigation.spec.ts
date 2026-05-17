import { expect, test } from '@playwright/test';

test.describe('Ghatana Studio navigation', () => {
  test('loads home and navigates to blueprint and canvas views', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Ghatana Studio' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Product development journey' })).toBeVisible();

    await page.getByRole('link', { name: 'Blueprints' }).click();
    await expect(page).toHaveURL(/\/blueprints$/);
    await expect(page.getByText('intent:yappc:commerce-studio:corr-yappc-1')).toBeVisible();

    await page.getByRole('link', { name: 'Canvas' }).click();
    await expect(page).toHaveURL(/\/canvas$/);
    await expect(page.getByText('evidence:graph-commerce')).toBeVisible();
  });

  test('shows disabled access message for ideas in unconfigured mode', async ({ page }) => {
    await page.goto('/ideas');

    await expect(page.getByText('Route access is disabled in this runtime mode.')).toBeVisible();
    await expect(page.getByText(/Required capability:/)).toBeVisible();
  });
});
