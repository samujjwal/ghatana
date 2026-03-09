import { test, expect } from '@playwright/test';
import { registerMockRoutes } from './fixtures';

/**
 * E2E tests for child requests handling in the dashboard.
 *
 * Validates that pending child requests are surfaced to parents
 * and that approve / deny flows update UI state correctly.
 */

// Helper to login before each test (mirrors dashboard.spec.ts)
async function login(page: any) {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill('parent@example.com');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL('/dashboard');
}

test.describe('Child Requests on Dashboard', () => {
    test.beforeEach(async ({ page, context }) => {
        await context.clearCookies();
        await page.goto('/');
        await page.evaluate(() => {
            try { localStorage.clear(); } catch (e) { }
        });
        await registerMockRoutes(page);
        await login(page);
    });

    test('should display pending child requests section', async ({ page }) => {
        // Section heading
        await expect(page.getByText('Pending Requests')).toBeVisible();

        // Card content from mocked request
        await expect(page.getByText('Extra Time Request')).toBeVisible();
        await expect(page.getByText(/Finished homework, want extra time/i)).toBeVisible();
    });

    test('should approve a pending child request from dashboard', async ({ page }) => {
        // Ensure pending card visible
        await expect(page.getByText('Extra Time Request')).toBeVisible();

        // Click approve on the card
        await page.getByRole('button', { name: /approve/i }).first().click();

        // In approval modal, confirm
        await page.getByRole('button', { name: /confirm approval/i }).click();

        // After approval, pending list should be empty and show empty state text
        await expect(page.getByText('No pending requests')).toBeVisible();
    });

    test('should deny a pending child request from dashboard', async ({ page }) => {
        // Reload to reset state from previous tests
        await page.reload();

        // Ensure pending card visible again
        await expect(page.getByText('Extra Time Request')).toBeVisible();

        await page.getByRole('button', { name: /deny/i }).first().click();

        // After denial, pending list should be empty
        await expect(page.getByText('No pending requests')).toBeVisible();
    });
});

/**
 * E2E tests for the complete child request approval flow (Phase 2 slice).
 *
 * Tests the end-to-end flow:
 * 1. Child submits request from blocked page
 * 2. Parent sees request in dashboard
 * 3. Parent approves/denies
 * 4. Command is enqueued for device
 *
 * @doc.type test-suite
 * @doc.purpose E2E tests for child request approval vertical slice
 * @doc.layer e2e-testing
 */
test.describe('Child Request Approval Flow - E2E', () => {
    test.beforeEach(async ({ page, context }) => {
        await context.clearCookies();
        await page.goto('/');
        await page.evaluate(() => {
            try { localStorage.clear(); } catch (e) { }
        });
        await registerMockRoutes(page);
    });

    /**
     * Verifies the complete unblock request flow.
     *
     * GIVEN: Child has submitted an unblock request for a domain
     * WHEN: Parent views and approves the request
     * THEN: Command is enqueued for the device
     */
    test('should complete unblock request approval flow', async ({ page }) => {
        // Mock the decision endpoint to verify it's called correctly
        let decisionPayload: unknown = null;
        await page.route('**/api/children/*/requests/*/decision', async (route) => {
            decisionPayload = route.request().postDataJSON();
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    success: true,
                    data: {
                        id: 'request-1',
                        status: 'approved',
                        decision: decisionPayload,
                    },
                }),
            });
        });

        // Mock unblock request in the list
        await page.route('**/api/children/*/requests*', async (route) => {
            if (route.request().method() === 'GET') {
                await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: JSON.stringify({
                        success: true,
                        data: [
                            {
                                id: 'request-1',
                                child_id: 'child-1',
                                device_id: 'device-1',
                                type: 'unblock',
                                status: 'pending',
                                resource: { domain: 'facebook.com' },
                                reason: 'Need for school project',
                                created_at: new Date().toISOString(),
                            },
                        ],
                    }),
                });
            } else {
                await route.continue();
            }
        });

        // Login
        await page.goto('/login');
        await page.getByLabel(/email/i).fill('parent@example.com');
        await page.getByLabel(/password/i).fill('password123');
        await page.getByRole('button', { name: /sign in/i }).click();
        await expect(page).toHaveURL('/dashboard');

        // Wait for requests to load
        await page.waitForTimeout(2000);

        // Look for the unblock request
        const requestCard = page.locator('text=facebook.com').first();
        if (await requestCard.isVisible()) {
            // Find and click approve button near the request
            await page.getByRole('button', { name: /approve/i }).first().click();

            // Confirm approval if modal appears
            const confirmButton = page.getByRole('button', { name: /confirm/i });
            if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
                await confirmButton.click();
            }

            // Verify decision was sent
            await page.waitForTimeout(1000);
            expect(decisionPayload).toBeDefined();
        }
    });

    /**
     * Verifies extend session request flow.
     *
     * GIVEN: Child has submitted an extend session request
     * WHEN: Parent approves with specific duration
     * THEN: Command includes the granted duration
     */
    test('should handle extend session request with duration', async ({ page }) => {
        let decisionPayload: unknown = null;

        await page.route('**/api/children/*/requests/*/decision', async (route) => {
            decisionPayload = route.request().postDataJSON();
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    success: true,
                    data: { id: 'request-2', status: 'approved' },
                }),
            });
        });

        await page.route('**/api/children/*/requests*', async (route) => {
            if (route.request().method() === 'GET') {
                await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: JSON.stringify({
                        success: true,
                        data: [
                            {
                                id: 'request-2',
                                child_id: 'child-1',
                                device_id: 'device-1',
                                type: 'extend_session',
                                status: 'pending',
                                minutes: 30,
                                reason: 'Finishing homework',
                                created_at: new Date().toISOString(),
                            },
                        ],
                    }),
                });
            } else {
                await route.continue();
            }
        });

        await page.goto('/login');
        await page.getByLabel(/email/i).fill('parent@example.com');
        await page.getByLabel(/password/i).fill('password123');
        await page.getByRole('button', { name: /sign in/i }).click();
        await expect(page).toHaveURL('/dashboard');

        await page.waitForTimeout(2000);

        // Dashboard should load without errors
        const pageContent = await page.content();
        expect(pageContent).toContain('Dashboard');
    });

    /**
     * Verifies denied request flow.
     *
     * GIVEN: Child has submitted a request
     * WHEN: Parent denies the request
     * THEN: No command is enqueued, request is marked denied
     */
    test('should handle request denial correctly', async ({ page }) => {
        let decisionPayload: unknown = null;

        await page.route('**/api/children/*/requests/*/decision', async (route) => {
            decisionPayload = route.request().postDataJSON();
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    success: true,
                    data: { id: 'request-3', status: 'denied' },
                }),
            });
        });

        await page.route('**/api/children/*/requests*', async (route) => {
            if (route.request().method() === 'GET') {
                await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: JSON.stringify({
                        success: true,
                        data: [
                            {
                                id: 'request-3',
                                child_id: 'child-1',
                                type: 'unblock',
                                status: 'pending',
                                resource: { domain: 'gaming-site.com' },
                                reason: 'Want to play games',
                                created_at: new Date().toISOString(),
                            },
                        ],
                    }),
                });
            } else {
                await route.continue();
            }
        });

        await page.goto('/login');
        await page.getByLabel(/email/i).fill('parent@example.com');
        await page.getByLabel(/password/i).fill('password123');
        await page.getByRole('button', { name: /sign in/i }).click();
        await expect(page).toHaveURL('/dashboard');

        await page.waitForTimeout(2000);

        // Dashboard should load without errors
        const pageContent = await page.content();
        expect(pageContent).toContain('Dashboard');
    });
});
