import { test, expect } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_TEST_BASE_URL || 'http://localhost:5173';

// Smoke tests for persona workspaces and Org Builder flows

test.describe('Persona workspace routes', () => {
    const personas = ['engineer', 'lead', 'sre', 'security', 'admin'] as const;

    for (const persona of personas) {
        test(`loads /workspace/${persona} workspace`, async ({ page }) => {
            await page.goto(`${BASE_URL}/workspace/${persona}`);

            // Expect persona-specific onboarding banner or workspace header
            await expect(page.locator('text=Workspace').first()).toBeVisible();
        });
    }
});


test.describe('Org Builder page', () => {
    test('loads Org Builder with filters and graph', async ({ page }) => {
        await page.goto(`${BASE_URL}/org?type=department`);

        await expect(page.locator('text=Org Builder')).toBeVisible();
        await expect(page.locator('text=Filters')).toBeVisible();
        await expect(page.locator('label:text("Department")')).toBeVisible();
    });
});


test.describe('Global filters and contextual hints surfaces', () => {
    test('dashboard shows GlobalFilterBar and contextual hints entry points', async ({ page }) => {
        await page.goto(`${BASE_URL}/dashboard`);

        // GlobalFilterBar persona filter
        await expect(page.locator('text=Persona').first()).toBeVisible();

        // The "Related" contextual hints label appears on surfaces where ContextualHints is rendered
        // Not all pages may show it, so we focus on reports/monitoring in a separate test.
    });

    test('reports page shows contextual hints', async ({ page }) => {
        await page.goto(`${BASE_URL}/reports`);

        await expect(page.locator('text=Reports')).toBeVisible();
        await expect(page.locator('text=Related')).toBeVisible();
    });

    test('real-time monitor page shows contextual hints and global filters', async ({ page }) => {
        await page.goto(`${BASE_URL}/realtime-monitor`);

        await expect(page.locator('text=Real-Time Monitor')).toBeVisible();
        await expect(page.locator('text=Related')).toBeVisible();
        await expect(page.locator('text=Environment')).toBeVisible();
    });
});
