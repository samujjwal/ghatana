/**
 * E2E Tests for Persona Multi-Tab Sync
 *
 * Tests validate:
 * - Real-time synchronization across multiple tabs
 * - WebSocket connection and reconnection
 * - Optimistic updates and conflict resolution
 * - Network failure recovery
 * - Authentication and workspace isolation
 */

import { test, expect, Browser, Page, BrowserContext } from '@playwright/test';

const BASE_URL = process.env.VITE_BASE_URL || 'http://localhost:3000';
const API_URL = process.env.VITE_API_URL || 'http://localhost:3001';

// Test credentials (from seed data)
const TEST_USER = {
    email: 'admin@example.com',
    password: 'demo123',
};

/**
 * Login helper
 */
async function login(page: Page) {
    await page.goto(`${BASE_URL}/login`);
    await page.fill('input[name="email"]', TEST_USER.email);
    await page.fill('input[name="password"]', TEST_USER.password);
    await page.click('button[type="submit"]');
    await page.waitForURL(`${BASE_URL}/dashboard`);
}

/**
 * Navigate to personas page
 */
async function goToPersonasPage(page: Page, workspaceId = 'default') {
    await page.goto(`${BASE_URL}/personas/${workspaceId}`);
    await page.waitForSelector('text=Base Roles');
}

/**
 * Get checkbox for role
 */
function getRoleCheckbox(page: Page, roleName: string) {
    return page.locator(`input[type="checkbox"][aria-label*="${roleName}"]`);
}

test.describe('Persona Multi-Tab Sync E2E', () => {
    let browser: Browser;
    let context1: BrowserContext;
    let context2: BrowserContext;
    let tab1: Page;
    let tab2: Page;

    test.beforeAll(async ({ browser: b }) => {
        browser = b;
    });

    test.beforeEach(async () => {
        // Create two independent browser contexts (simulates 2 tabs)
        context1 = await browser.newContext();
        context2 = await browser.newContext();

        tab1 = await context1.newPage();
        tab2 = await context2.newPage();

        // Login both tabs
        await login(tab1);
        await login(tab2);

        // Navigate to personas page
        await goToPersonasPage(tab1);
        await goToPersonasPage(tab2);
    });

    test.afterEach(async () => {
        await context1.close();
        await context2.close();
    });

    test('should sync role selection across tabs', async () => {
        // Verify sync status is connected in both tabs
        await expect(tab1.locator('text=Real-time sync active')).toBeVisible();
        await expect(tab2.locator('text=Real-time sync active')).toBeVisible();

        // Get initial state in tab 2
        const techLeadCheckboxTab2 = getRoleCheckbox(tab2, 'Tech Lead');
        const initialCheckedTab2 = await techLeadCheckboxTab2.isChecked();

        // Toggle Tech Lead role in tab 1
        const techLeadCheckboxTab1 = getRoleCheckbox(tab1, 'Tech Lead');
        await techLeadCheckboxTab1.click();

        // Click save in tab 1
        await tab1.click('button:has-text("Save")');

        // Wait for success alert
        await tab1.waitForSelector('text=Persona preferences saved successfully!');

        // Verify tab 2 updates automatically (within 500ms)
        await tab2.waitForTimeout(500);

        const updatedCheckedTab2 = await techLeadCheckboxTab2.isChecked();
        expect(updatedCheckedTab2).not.toBe(initialCheckedTab2);
    });

    test('should sync role deselection across tabs', async () => {
        // Ensure admin role is selected in both tabs initially
        const adminCheckboxTab1 = getRoleCheckbox(tab1, 'Administrator');
        const adminCheckboxTab2 = getRoleCheckbox(tab2, 'Administrator');

        await expect(adminCheckboxTab1).toBeChecked();
        await expect(adminCheckboxTab2).toBeChecked();

        // Deselect admin role in tab 1
        await adminCheckboxTab1.click();
        await tab1.click('button:has-text("Save")');
        await tab1.waitForSelector('text=Persona preferences saved successfully!');

        // Verify tab 2 deselects automatically
        await tab2.waitForTimeout(500);
        await expect(adminCheckboxTab2).not.toBeChecked();
    });

    test('should sync multiple role changes', async () => {
        const techLeadTab1 = getRoleCheckbox(tab1, 'Tech Lead');
        const techLeadTab2 = getRoleCheckbox(tab2, 'Tech Lead');

        // Make multiple changes in tab 1
        await techLeadTab1.click(); // Select
        await getRoleCheckbox(tab1, 'Developer').click(); // Deselect
        await tab1.click('button:has-text("Save")');
        await tab1.waitForSelector('text=Persona preferences saved successfully!');

        // Verify all changes synced to tab 2
        await tab2.waitForTimeout(500);
        await expect(techLeadTab2).toBeChecked();
        await expect(getRoleCheckbox(tab2, 'Developer')).not.toBeChecked();
    });

    test('should show sync status when WebSocket disconnects', async () => {
        // Verify connected initially
        await expect(tab1.locator('text=Real-time sync active')).toBeVisible();

        // Simulate network failure by going offline
        await context1.setOffline(true);

        // Wait for disconnect detection (should show error banner)
        await tab1.waitForSelector('text=Real-time sync unavailable', { timeout: 10000 });

        // Reconnect
        await context1.setOffline(false);

        // Should reconnect and show connected status
        await tab1.waitForSelector('text=Real-time sync active', { timeout: 10000 });
    });

    test('should handle optimistic updates correctly', async () => {
        const techLeadTab1 = getRoleCheckbox(tab1, 'Tech Lead');

        // Toggle role (optimistic update should be instant)
        const toggleStartTime = Date.now();
        await techLeadTab1.click();
        const uiUpdateTime = Date.now() - toggleStartTime;

        // UI should update in <100ms (optimistic)
        expect(uiUpdateTime).toBeLessThan(100);

        // Verify checkbox is checked immediately
        await expect(techLeadTab1).toBeChecked();

        // Now save (actual API call)
        await tab1.click('button:has-text("Save")');
        await tab1.waitForSelector('text=Persona preferences saved successfully!');
    });

    test('should handle concurrent updates from multiple tabs', async () => {
        // Start with both tabs making changes simultaneously
        const techLeadTab1 = getRoleCheckbox(tab1, 'Tech Lead');
        const adminTab2 = getRoleCheckbox(tab2, 'Administrator');

        // Make different changes in each tab
        await Promise.all([techLeadTab1.click(), adminTab2.click()]);

        // Save in tab 1
        await tab1.click('button:has-text("Save")');
        await tab1.waitForSelector('text=Persona preferences saved successfully!');

        // Wait briefly, then save in tab 2
        await tab2.waitForTimeout(200);
        await tab2.click('button:has-text("Save")');
        await tab2.waitForSelector('text=Persona preferences saved successfully!');

        // Both changes should be reflected (last write wins)
        // Tab 2's change (admin deselected) should be final state
        await tab1.waitForTimeout(500);
        await expect(getRoleCheckbox(tab1, 'Administrator')).not.toBeChecked();
        await expect(getRoleCheckbox(tab1, 'Tech Lead')).toBeChecked();
    });

    test('should validate role count across tabs', async () => {
        // Try to select 6th role in tab 1 (should be prevented)
        const roles = ['Administrator', 'Developer', 'Tech Lead', 'Reviewer', 'Architect'];

        // Select 5 roles
        for (const role of roles.slice(0, 5)) {
            const checkbox = getRoleCheckbox(tab1, role);
            if (!(await checkbox.isChecked())) {
                await checkbox.click();
            }
        }

        // Try to select 6th role (should show alert or be disabled)
        const sixthRole = getRoleCheckbox(tab1, roles[5] || 'Observer');

        // Listen for alert dialog
        tab1.on('dialog', async (dialog) => {
            expect(dialog.message()).toContain('Maximum 5 roles allowed');
            await dialog.accept();
        });

        await sixthRole.click();
        await tab1.waitForTimeout(500);

        // 6th role should not be checked
        await expect(sixthRole).not.toBeChecked();
    });

    test('should persist changes after page reload', async () => {
        // Make change in tab 1
        const techLeadTab1 = getRoleCheckbox(tab1, 'Tech Lead');
        await techLeadTab1.click();
        await tab1.click('button:has-text("Save")');
        await tab1.waitForSelector('text=Persona preferences saved successfully!');

        // Reload tab 2
        await tab2.reload();
        await tab2.waitForSelector('text=Base Roles');

        // Change should persist
        const techLeadTab2 = getRoleCheckbox(tab2, 'Tech Lead');
        await expect(techLeadTab2).toBeChecked();
    });

    test('should handle rapid save clicks gracefully', async () => {
        const techLeadTab1 = getRoleCheckbox(tab1, 'Tech Lead');
        await techLeadTab1.click();

        // Click save multiple times rapidly
        const saveButton = tab1.locator('button:has-text("Save")');
        await saveButton.click();
        await saveButton.click();
        await saveButton.click();

        // Should only save once (button should be disabled during save)
        await tab1.waitForSelector('text=Persona preferences saved successfully!', {
            timeout: 5000,
        });

        // No multiple success messages
        const successMessages = await tab1.locator('text=Persona preferences saved successfully!')
            .count();
        expect(successMessages).toBe(1);
    });

    test('should show loading state during save', async () => {
        const techLeadTab1 = getRoleCheckbox(tab1, 'Tech Lead');
        await techLeadTab1.click();

        // Click save
        const saveButton = tab1.locator('button:has-text("Save")');
        await saveButton.click();

        // Button should show loading state
        await expect(tab1.locator('button:has-text("Saving")')).toBeVisible({
            timeout: 1000,
        });

        // Wait for completion
        await tab1.waitForSelector('text=Persona preferences saved successfully!');

        // Button should return to normal
        await expect(saveButton).toBeVisible();
    });

    test('should handle workspace isolation', async () => {
        // Navigate tab 1 to workspace-1
        await goToPersonasPage(tab1, 'workspace-1');

        // Navigate tab 2 to workspace-2
        await goToPersonasPage(tab2, 'workspace-2');

        // Make change in tab 1 (workspace-1)
        const techLeadTab1 = getRoleCheckbox(tab1, 'Tech Lead');
        await techLeadTab1.click();
        await tab1.click('button:has-text("Save")');
        await tab1.waitForSelector('text=Persona preferences saved successfully!');

        // Tab 2 (workspace-2) should NOT be affected
        await tab2.waitForTimeout(500);
        const techLeadTab2 = getRoleCheckbox(tab2, 'Tech Lead');

        // If workspace-2 has different initial state, it should remain unchanged
        const tab2State = await techLeadTab2.isChecked();
        await tab2.reload();
        await tab2.waitForSelector('text=Base Roles');

        const tab2StateAfterReload = await getRoleCheckbox(tab2, 'Tech Lead').isChecked();
        expect(tab2StateAfterReload).toBe(tab2State);
    });
});

test.describe('Persona Sync Error Handling', () => {
    test('should recover from API errors', async ({ page }) => {
        await login(page);
        await goToPersonasPage(page);

        // Block API requests temporarily
        await page.route(`${API_URL}/api/personas/*`, (route) => {
            route.abort('failed');
        });

        // Try to save (should fail)
        const techLead = getRoleCheckbox(page, 'Tech Lead');
        await techLead.click();
        await page.click('button:has-text("Save")');

        // Should show error
        await page.waitForSelector('text=Failed to save', { timeout: 5000 });

        // Unblock API
        await page.unroute(`${API_URL}/api/personas/*`);

        // Retry save (should succeed)
        await page.click('button:has-text("Save")');
        await page.waitForSelector('text=Persona preferences saved successfully!');
    });

    test('should handle network timeout', async ({ page }) => {
        await login(page);
        await goToPersonasPage(page);

        // Slow down API responses to simulate timeout
        await page.route(`${API_URL}/api/personas/*`, async (route) => {
            await new Promise((resolve) => setTimeout(resolve, 10000)); // 10s delay
            await route.continue();
        });

        // Try to save (should timeout or show loading for long time)
        const techLead = getRoleCheckbox(page, 'Tech Lead');
        await techLead.click();
        await page.click('button:has-text("Save")');

        // Should show loading state
        await expect(page.locator('button:has-text("Saving")')).toBeVisible();

        // Wait for timeout or cancel
        // (In production, implement request timeout)
    });

    test('should reset to server state on error', async ({ page }) => {
        await login(page);
        await goToPersonasPage(page);

        // Get initial state
        const techLead = getRoleCheckbox(page, 'Tech Lead');
        const initialState = await techLead.isChecked();

        // Make change
        await techLead.click();
        expect(await techLead.isChecked()).not.toBe(initialState);

        // Block save
        await page.route(`${API_URL}/api/personas/*`, (route) => {
            route.abort('failed');
        });

        // Try to save (will fail)
        await page.click('button:has-text("Save")');
        await page.waitForSelector('text=Failed to save');

        // Click reset (should revert to server state)
        await page.click('button:has-text("Reset")');

        // Should revert to initial state
        await page.waitForTimeout(500);
        expect(await techLead.isChecked()).toBe(initialState);
    });
});

test.describe('Persona Sync Performance', () => {
    test('should sync within 500ms', async ({ browser }) => {
        const context1 = await browser.newContext();
        const context2 = await browser.newContext();
        const tab1 = await context1.newPage();
        const tab2 = await context2.newPage();

        await login(tab1);
        await login(tab2);
        await goToPersonasPage(tab1);
        await goToPersonasPage(tab2);

        // Get initial state
        const techLeadTab2 = getRoleCheckbox(tab2, 'Tech Lead');
        const initialState = await techLeadTab2.isChecked();

        // Make change in tab 1 and record time
        const startTime = Date.now();

        const techLeadTab1 = getRoleCheckbox(tab1, 'Tech Lead');
        await techLeadTab1.click();
        await tab1.click('button:has-text("Save")');
        await tab1.waitForSelector('text=Persona preferences saved successfully!');

        // Wait for tab 2 to update
        await tab2.waitForTimeout(500);
        const updatedState = await techLeadTab2.isChecked();

        const syncTime = Date.now() - startTime;

        // Verify sync happened
        expect(updatedState).not.toBe(initialState);

        // Sync should complete within 500ms
        expect(syncTime).toBeLessThan(500);

        await context1.close();
        await context2.close();
    });

    test('should handle 10+ rapid updates', async ({ page }) => {
        await login(page);
        await goToPersonasPage(page);

        const techLead = getRoleCheckbox(page, 'Tech Lead');

        // Toggle role 10 times rapidly
        for (let i = 0; i < 10; i++) {
            await techLead.click();
            await page.waitForTimeout(50); // 50ms between clicks
        }

        // Save final state
        await page.click('button:has-text("Save")');
        await page.waitForSelector('text=Persona preferences saved successfully!');

        // Should save successfully without errors
        await expect(techLead).toBeChecked(); // Final state should be checked
    });
});
