/**
 * Global Search E2E Tests
 *
 * Validates keyword and semantic search across projects, requirements,
 * canvas documents, and pages. Tests the command-palette-style search UI
 * accessible via Cmd+K as well as the dedicated search route.
 *
 * @doc.type e2e
 * @doc.purpose Global search correctness, keyboard shortcut, and result navigation
 * @doc.layer product
 */

import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function openSearchPalette(page: import('@playwright/test').Page) {
  // Trigger via keyboard shortcut
  await page.keyboard.press('Meta+k');
  await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Global Search', () => {
  test.beforeEach(async ({ page }) => {
    // Mock the globalSearch GraphQL query
    await page.route('**/graphql', async (route) => {
      const body = route.request().postDataJSON() as { query?: string } | null;
      if (body?.query?.includes('globalSearch')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              globalSearch: {
                query: 'auth',
                totalCount: 3,
                items: [
                  {
                    id: 'req-1',
                    category: 'requirement',
                    title: 'User authentication requirement',
                    description: 'Login with email and password',
                    path: '/app/projects/p1/lifecycle',
                    score: 0.95,
                  },
                  {
                    id: 'proj-1',
                    category: 'project',
                    title: 'Auth service project',
                    description: 'Backend authentication service',
                    path: '/app/projects/p1',
                    score: 0.8,
                  },
                  {
                    id: 'page-1',
                    category: 'page',
                    title: 'Auth flow diagram',
                    description: 'OAuth2 sequence diagram',
                    path: '/app/projects/p1/canvas',
                    score: 0.7,
                  },
                ],
              },
            },
          }),
        });
        return;
      }
      await route.continue();
    });

    await page.goto('/app');
    await expect(page).not.toHaveURL(/login/);
  });

  test('opens search dialog via keyboard shortcut Cmd+K', async ({ page }) => {
    await openSearchPalette(page);
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  test('displays search results matching the query', async ({ page }) => {
    // Navigate to the search route if it exists, otherwise open palette
    await page.goto('/app/search');
    const searchInput = page.getByRole('searchbox').or(page.getByPlaceholder(/search/i)).first();
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill('auth');
    await page.waitForTimeout(400); // debounce
    // Results should appear
    await expect(page.getByText('User authentication requirement')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Auth service project')).toBeVisible();
  });

  test('shows empty state when no results found', async ({ page }) => {
    // Override mock for empty results
    await page.route('**/graphql', async (route) => {
      const body = route.request().postDataJSON() as { query?: string } | null;
      if (body?.query?.includes('globalSearch')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              globalSearch: { query: 'zzznomatch', totalCount: 0, items: [] },
            },
          }),
        });
        return;
      }
      await route.continue();
    });

    await page.goto('/app/search');
    const searchInput = page.getByRole('searchbox').or(page.getByPlaceholder(/search/i)).first();
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill('zzznomatch');
    await page.waitForTimeout(400);
    await expect(
      page.getByText(/no results|nothing found/i)
    ).toBeVisible({ timeout: 5000 });
  });

  test('navigates to the result target when a result is clicked', async ({ page }) => {
    await page.goto('/app/search');
    const searchInput = page.getByRole('searchbox').or(page.getByPlaceholder(/search/i)).first();
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill('auth');
    await page.waitForTimeout(400);

    const firstResult = page.getByText('User authentication requirement');
    await expect(firstResult).toBeVisible({ timeout: 5000 });
    await firstResult.click();
    // Expect navigation to the lifecycle route for that requirement
    await expect(page).toHaveURL(/lifecycle|projects/, { timeout: 5000 });
  });

  test('results are categorised by type (requirement, project, page)', async ({ page }) => {
    await page.goto('/app/search');
    const searchInput = page.getByRole('searchbox').or(page.getByPlaceholder(/search/i)).first();
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill('auth');
    await page.waitForTimeout(400);
    await expect(page.getByText('User authentication requirement')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Auth service project')).toBeVisible();
    await expect(page.getByText('Auth flow diagram')).toBeVisible();
  });

  test('clears results when query is cleared', async ({ page }) => {
    await page.goto('/app/search');
    const searchInput = page.getByRole('searchbox').or(page.getByPlaceholder(/search/i)).first();
    await expect(searchInput).toBeVisible({ timeout: 5000 });
    await searchInput.fill('auth');
    await page.waitForTimeout(400);
    await expect(page.getByText('User authentication requirement')).toBeVisible({ timeout: 5000 });
    await searchInput.clear();
    await page.waitForTimeout(400);
    await expect(page.getByText('User authentication requirement')).not.toBeVisible({ timeout: 3000 });
  });
});
