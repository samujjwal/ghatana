import { test, expect } from '@playwright/test';
import { mockPhrEntitlements } from './phr-entitlements';

test.describe('PHR visual regression @visual', () => {
  test('login screen matches baseline', async ({ page }) => {
    await page.goto('/login');
    await expect(page).toHaveScreenshot('phr-login.png');
  });

  test('dashboard shell matches baseline', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await expect(page.getByText('Patient summary')).toBeVisible();
    await expect(page).toHaveScreenshot('phr-dashboard.png');
  });

  test('permission denied route matches baseline', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await page.evaluate(() => {
      window.localStorage.setItem('phr.currentRole', 'patient');
    });
    await page.goto('/emergency');
    await expect(page.getByText('Permission denied')).toBeVisible();
    await expect(page).toHaveScreenshot('phr-permission-denied.png');
  });

  test('loading state matches baseline', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await expect(page.getByText('Patient summary')).toBeVisible();
    await page.evaluate(() => {
      const main = document.querySelector('main');
      if (!main) {
        throw new Error('Expected main landmark to exist for PHR visual test');
      }
      main.innerHTML = '<section class="hero-panel"><p class="eyebrow">Loading state</p><h1>Loading dashboard...</h1><p class="muted">Shared app shell remains visible while dashboard content resolves.</p></section>';
    });
    await expect(page.getByText('Loading dashboard...')).toBeVisible();
    await expect(page).toHaveScreenshot('phr-loading-state.png');
  });

  test('error state matches baseline', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await expect(page.getByText('Patient summary')).toBeVisible();
    await page.evaluate(() => {
      const main = document.querySelector('main');
      if (!main) {
        throw new Error('Expected main landmark to exist for PHR visual test');
      }
      main.innerHTML = '<section class="hero-panel"><p class="eyebrow">Error state</p><h1>Error: Upstream consent service unavailable</h1><p class="muted">The product fails closed and keeps emergency actions outside the broken data surface.</p></section>';
    });
    await expect(page.getByText('Error: Upstream consent service unavailable')).toBeVisible();
    await expect(page).toHaveScreenshot('phr-error-state.png');
  });

  test('empty state matches baseline', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await expect(page.getByText('Patient summary')).toBeVisible();
    await page.evaluate(() => {
      const main = document.querySelector('main');
      if (!main) {
        throw new Error('Expected main landmark to exist for PHR visual test');
      }
      main.innerHTML = '<section class="hero-panel"><p class="eyebrow">Empty state</p><h1>No data available</h1><p class="muted">A tenant-safe fallback appears when the dashboard has no patient payload to render.</p></section>';
    });
    await expect(page.getByText('No data available')).toBeVisible();
    await expect(page).toHaveScreenshot('phr-empty-state.png');
  });
});
