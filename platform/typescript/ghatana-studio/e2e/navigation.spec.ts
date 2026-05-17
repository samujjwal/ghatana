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

  test('Studio-visible lifecycle pilot E2E flow', async ({ page }) => {
    await page.goto('/lifecycle');

    // Verify lifecycle page loads
    await expect(page.getByRole('heading', { name: 'Lifecycle' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Product lifecycle execution' })).toBeVisible();

    // Verify pilot readiness panel is visible
    await expect(page.getByText('Lifecycle pilot readiness')).toBeVisible();
    await expect(page.getByText('Current product unit:')).toBeVisible();

    // Verify approval queue is displayed
    await expect(page.getByText('Approval queue')).toBeVisible();

    // Verify lifecycle run history is displayed
    await expect(page.getByText('Lifecycle run history')).toBeVisible();

    // Verify validation command is shown
    await expect(page.getByText('Validation command')).toBeVisible();
  });
});
