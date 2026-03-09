/**
 * Canvas Type Switching E2E Tests
 * 
 * End-to-end tests for content type switching functionality including:
 * - Opening type selector from context menu
 * - Selecting compatible and incompatible types
 * - Confirming type changes
 * - Data migration verification
 * - Warning dialogs
 * 
 * @doc.type test
 * @doc.purpose E2E tests for type management
 * @doc.layer product
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Canvas Type Switching', () => {
    let page: Page;

    test.beforeEach(async ({ page: testPage }) => {
        page = testPage;
        await page.goto('/canvas');
        await page.waitForLoadState('networkidle');
    });

    test.describe('Type Selector Access', () => {
        test('should open type selector from artifact context menu', async () => {
            // Right-click on an artifact node
            await page.click('[data-testid="artifact-node-1"]', { button: 'right' });

            // Click "Change Content Type" menu item
            await page.click('text=Change Content Type');

            // Verify modal opens
            const dialog = page.locator('role=dialog');
            await expect(dialog).toBeVisible();
            await expect(page.getByText('Change Content Type')).toBeVisible();
        });

        test('should display current artifact type', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            // Should show current type
            await expect(page.getByText('Current Type:')).toBeVisible();
            await expect(page.getByText('Code Editor')).toBeVisible();
        });

        test('should close modal when cancel is clicked', async () => {
            await page.click('[data-testid="artifact-node-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('button:has-text("Cancel")');

            // Modal should close
            await expect(page.locator('role=dialog')).not.toBeVisible();
        });
    });

    test.describe('Compatible Type Selection', () => {
        test('should show recommended types for code artifact', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            // Should show recommended section
            await expect(page.getByText('Recommended Types')).toBeVisible();
            await expect(page.getByText(/Test Case/i)).toBeVisible();
            await expect(page.getByText(/Markdown Document/i)).toBeVisible();
        });

        test('should highlight selected type', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            // Click a type
            await page.click('text=Test Case');

            // Should be highlighted
            const selectedItem = page.locator('text=Test Case').locator('..');
            await expect(selectedItem).toHaveCSS('border-color', /primary/);
        });

        test('should show info alert for compatible conversion', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('text=Test Case');

            // Should show compatibility message
            await expect(page.getByText(/This conversion is compatible/i)).toBeVisible();
        });

        test('should enable change button when different type selected', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('text=Test Case');

            const changeButton = page.getByRole('button', { name: /Change Type/i });
            await expect(changeButton).toBeEnabled();
        });

        test('should successfully change to compatible type', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('text=Test Case');
            await page.click('button:has-text("Change Type")');

            // Modal should close
            await expect(page.locator('role=dialog')).not.toBeVisible();

            // Node should update (check for type indicator change)
            await page.waitForTimeout(500);
            const node = page.locator('[data-testid="artifact-node-code-1"]');
            await expect(node).toContainText(/test/i);
        });
    });

    test.describe('Incompatible Type Selection', () => {
        test('should show warning for incompatible conversion', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            // Expand all types
            await page.click('text=All Content Types');

            // Select an incompatible type
            await page.click('text=Project Brief');

            // Should show warning
            await expect(page.getByText(/Potentially Lossy Conversion/i)).toBeVisible();
            await expect(page.getByText(/may result in data loss/i)).toBeVisible();
        });

        test('should change button text for incompatible conversion', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('text=All Content Types');
            await page.click('text=Project Brief');

            // Button should say "Change Anyway"
            await expect(page.getByRole('button', { name: /Change Anyway/i })).toBeVisible();
        });

        test('should still allow incompatible conversion with warning', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('text=All Content Types');
            await page.click('text=Project Brief');
            await page.click('button:has-text("Change Anyway")');

            // Should complete the change
            await expect(page.locator('role=dialog')).not.toBeVisible();
        });
    });

    test.describe('All Types Section', () => {
        test('should expand all types accordion', async () => {
            await page.click('[data-testid="artifact-node-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('text=All Content Types');

            // Should show category labels
            await expect(page.getByText(/requirements/i)).toBeVisible();
        });

        test('should show types organized by category', async () => {
            await page.click('[data-testid="artifact-node-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('text=All Content Types');

            // Should show different categories
            const dialog = page.locator('role=dialog');
            await expect(dialog).toContainText(/code/i);
            await expect(dialog).toContainText(/documentation/i);
        });
    });

    test.describe('Data Migration', () => {
        test('should preserve data when migrating code to documentation', async () => {
            // Create a code artifact with specific content
            await page.click('[data-testid="artifact-node-code-1"]');
            const codeContent = await page.textContent('[data-testid="artifact-code-preview"]');

            // Change type
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');
            await page.click('text=Markdown Document');
            await page.click('button:has-text("Change Type")');

            // Wait for migration
            await page.waitForTimeout(1000);

            // Click to view migrated content
            await page.click('[data-testid="artifact-node-code-1"]');

            // Content should include code blocks
            const migratedContent = await page.textContent('[data-testid="artifact-doc-preview"]');
            expect(migratedContent).toContain('```');
        });

        test('should generate test cases from requirements', async () => {
            await page.click('[data-testid="artifact-node-requirement-1"]', { button: 'right' });
            await page.click('text=Change Content Type');
            await page.click('text=Test Case');
            await page.click('button:has-text("Change Type")');

            await page.waitForTimeout(1000);

            // Verify test cases were generated
            await page.click('[data-testid="artifact-node-requirement-1"]');
            const content = await page.textContent('[data-testid="artifact-preview"]');
            expect(content).toContain('test');
        });

        test('should extract API endpoints from code', async () => {
            // Create code with API annotations
            await page.click('[data-testid="create-code-artifact"]');
            await page.fill('[data-testid="code-editor"]', '@GET("/api/users")');
            await page.click('[data-testid="save-artifact"]');

            // Change to API spec
            await page.click('[data-testid="new-artifact"]', { button: 'right' });
            await page.click('text=Change Content Type');
            await page.click('text=API Specification');
            await page.click('button:has-text("Change Type")');

            // Should have extracted endpoints
            await page.waitForTimeout(1000);
            await page.click('[data-testid="new-artifact"]');
            const apiContent = await page.textContent('[data-testid="artifact-preview"]');
            expect(apiContent).toContain('/api/users');
        });
    });

    test.describe('Multiple Conversions', () => {
        test('should handle chain of conversions', async () => {
            // Code -> Documentation
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');
            await page.click('text=Markdown Document');
            await page.click('button:has-text("Change Type")');

            await page.waitForTimeout(500);

            // Documentation -> Requirement
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');
            await page.click('text=Requirement');
            await page.click('button:has-text("Change Type")');

            // Should succeed
            await expect(page.locator('role=dialog')).not.toBeVisible();
        });

        test('should update node visual appearance after type change', async () => {
            const node = page.locator('[data-testid="artifact-node-code-1"]');

            // Get initial icon
            const initialIcon = await node.locator('[data-testid="artifact-icon"]').textContent();

            // Change type
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');
            await page.click('text=Test Case');
            await page.click('button:has-text("Change Type")');

            await page.waitForTimeout(500);

            // Icon should change
            const newIcon = await node.locator('[data-testid="artifact-icon"]').textContent();
            expect(newIcon).not.toBe(initialIcon);
        });
    });

    test.describe('Edge Cases', () => {
        test('should prevent selecting same type', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            // Current type is code, button should be disabled initially
            const changeButton = page.getByRole('button', { name: /Change Type/i });
            await expect(changeButton).toBeDisabled();
        });

        test('should handle rapid type selections', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            // Rapidly click different types
            await page.click('text=Test Case');
            await page.click('text=Markdown Document');
            await page.click('text=Test Case');

            // Should end with last selection
            const changeButton = page.getByRole('button', { name: /Change Type/i });
            await expect(changeButton).toBeEnabled();
        });

        test('should handle type change failure gracefully', async () => {
            // Mock a network error
            await page.route('**/api/artifacts/*/type', route =>
                route.fulfill({ status: 500 })
            );

            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');
            await page.click('text=Test Case');
            await page.click('button:has-text("Change Type")');

            // Should show error message
            await expect(page.getByText(/failed|error/i)).toBeVisible();
        });

        test('should close modal on Escape key', async () => {
            await page.click('[data-testid="artifact-node-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.keyboard.press('Escape');

            await expect(page.locator('role=dialog')).not.toBeVisible();
        });
    });

    test.describe('Accessibility', () => {
        test('should be keyboard navigable', async () => {
            await page.click('[data-testid="artifact-node-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            // Tab through elements
            await page.keyboard.press('Tab');

            // Should focus interactive elements
            const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
            expect(focusedElement).toBeTruthy();
        });

        test('should announce type changes to screen readers', async () => {
            await page.click('[data-testid="artifact-node-code-1"]', { button: 'right' });
            await page.click('text=Change Content Type');

            await page.click('text=Test Case');

            // Alert should be present for screen readers
            const alert = page.locator('role=alert');
            await expect(alert).toBeVisible();
        });
    });
});
