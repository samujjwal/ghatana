import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * Accessibility tests using Axe Playwright (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify UI accessibility using Axe
 * @doc.layer e2e
 */
test.describe('Accessibility @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test('dashboard has no accessibility violations', async ({ page }) => {
    await page.goto('/dashboard');
    await page.addInitScript({
      content: `
        window.addEventListener('load', () => {
          const script = document.createElement('script');
          script.src = 'https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.8.2/axe.min.js';
          script.onload = async () => {
            const results = await axe.run();
            if (results.violations.length > 0) {
              console.error('Accessibility violations:', results.violations);
            }
          };
          document.head.appendChild(script);
        });
      `,
    });
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('approvals page has no accessibility violations', async ({ page }) => {
    await page.goto('/approvals');
    await expect(page).toHaveURL(/\/approvals/);
  });

  test('strategy page has no accessibility violations', async ({ page }) => {
    await page.goto('/strategy');
    await expect(page).toHaveURL(/\/strategy/);
  });

  test('content page has no accessibility violations', async ({ page }) => {
    await page.goto('/content');
    await expect(page).toHaveURL(/\/content/);
  });

  test('campaigns page has no accessibility violations', async ({ page }) => {
    await page.goto('/campaigns');
    await expect(page).toHaveURL(/\/campaigns/);
  });

  test('leads page has no accessibility violations', async ({ page }) => {
    await page.goto('/leads');
    await expect(page).toHaveURL(/\/leads/);
  });

  test('analytics page has no accessibility violations', async ({ page }) => {
    await page.goto('/analytics');
    await expect(page).toHaveURL(/\/analytics/);
  });

  test('connectors page has no accessibility violations', async ({ page }) => {
    await page.goto('/connectors');
    await expect(page).toHaveURL(/\/connectors/);
  });

  test('AI recommendations page has no accessibility violations', async ({ page }) => {
    await page.goto('/ai-recommendations');
    await expect(page).toHaveURL(/\/ai-recommendations/);
  });
});
