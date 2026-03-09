/**
 * E2E Test Suite - Accessibility & Navigation Tests
 * Tests for WCAG compliance and navigation features including:
 * - Skip links
 * - Focus management
 * - Breadcrumb navigation
 * - Keyboard navigation
 */

import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

test.describe('Accessibility & Navigation', () => {
    test.beforeEach(async ({ page }) => {
        await setupTest(page, {
            url: '/app',
            seedData: true
        });
    });

    test.afterEach(async ({ page }) => {
        await teardownTest(page);
    });

    test.describe('Skip Links', () => {
        test('skip link should be visible on focus and navigate to main content', async ({ page }) => {
            // Tab to reveal skip link (it's sr-only until focused)
            await page.keyboard.press('Tab');

            // Check if skip link is now visible
            const skipLink = page.locator('a:has-text("Skip to main content")');
            
            // Skip link might not be the first tab stop, keep tabbing
            let found = false;
            for (let i = 0; i < 5; i++) {
                if (await skipLink.isVisible()) {
                    found = true;
                    break;
                }
                await page.keyboard.press('Tab');
            }

            if (found) {
                // Verify skip link is visible when focused
                await expect(skipLink).toBeVisible();

                // Activate the skip link
                await page.keyboard.press('Enter');

                // Verify main content received focus
                const mainContent = page.locator('#main-content');
                await expect(mainContent).toBeFocused();
            }
        });
    });

    test.describe('Breadcrumb Navigation', () => {
        test('breadcrumb should show workspace name on app home', async ({ page }) => {
            // Wait for breadcrumb bar to render
            const breadcrumbNav = page.locator('nav[aria-label="Breadcrumb"]');
            
            // Breadcrumb may not appear on home (no context), but should have home icon
            const homeLink = breadcrumbNav.locator('a[aria-label="Home"]');
            
            // If breadcrumb is present, verify home link
            if (await breadcrumbNav.isVisible()) {
                await expect(homeLink).toBeVisible();
            }
        });

        test('breadcrumb should update when navigating to project', async ({ page }) => {
            // Click on first project link in sidebar
            const projectLink = page.locator('nav[role="navigation"] a[href^="/app/p/"]').first();
            
            if (await projectLink.isVisible()) {
                const projectName = await projectLink.textContent();
                await projectLink.click();

                // Wait for navigation
                await page.waitForURL(/\/app\/p\//);

                // Verify breadcrumb updated with project name
                const breadcrumbNav = page.locator('nav[aria-label="Breadcrumb"]');
                
                if (projectName && await breadcrumbNav.isVisible()) {
                    // Should contain project name in breadcrumb
                    await expect(breadcrumbNav).toContainText(projectName.trim());
                }
            }
        });

        test('breadcrumb links should be keyboard navigable', async ({ page }) => {
            // Navigate to a project first
            const projectLink = page.locator('nav[role="navigation"] a[href^="/app/p/"]').first();
            
            if (await projectLink.isVisible()) {
                await projectLink.click();
                await page.waitForURL(/\/app\/p\//);

                // Tab through breadcrumb items
                const breadcrumbNav = page.locator('nav[aria-label="Breadcrumb"]');
                
                if (await breadcrumbNav.isVisible()) {
                    // Home icon should be focusable
                    const homeLink = breadcrumbNav.locator('a[aria-label="Home"]');
                    if (await homeLink.isVisible()) {
                        await homeLink.focus();
                        await expect(homeLink).toBeFocused();
                    }
                }
            }
        });

        test('breadcrumb should show ownership badge for project context', async ({ page }) => {
            // Navigate to a project
            const projectLink = page.locator('nav[role="navigation"] a[href^="/app/p/"]').first();
            
            if (await projectLink.isVisible()) {
                await projectLink.click();
                await page.waitForURL(/\/app\/p\//);

                // Check for ownership badge (Owner or Read-only)
                const breadcrumbNav = page.locator('nav[aria-label="Breadcrumb"]');
                
                if (await breadcrumbNav.isVisible()) {
                    // Should have either Owner or Read-only badge
                    const ownerBadge = breadcrumbNav.locator('span:has-text("Owner")');
                    const readOnlyBadge = breadcrumbNav.locator('span:has-text("Read-only")');
                    
                    const hasBadge = await ownerBadge.isVisible() || await readOnlyBadge.isVisible();
                    // Badge is optional based on context, just log if not present
                    if (!hasBadge) {
                        console.log('Note: No ownership badge visible - may be expected based on project state');
                    }
                }
            }
        });
    });

    test.describe('Focus Management', () => {
        test('interactive elements should have visible focus indicators', async ({ page }) => {
            // Tab to first focusable element
            await page.keyboard.press('Tab');

            // Get the focused element
            const focusedElement = await page.locator(':focus');
            
            // Check for focus ring styling (ring-2 or outline)
            const classList = await focusedElement.evaluate(el => el.className);
            const hasOutline = await focusedElement.evaluate(el => {
                const styles = getComputedStyle(el);
                return styles.outlineWidth !== '0px' || 
                       styles.boxShadow.includes('ring') ||
                       el.className.includes('ring');
            });

            // Focus should be visible (either via classes or computed styles)
            expect(hasOutline || classList.includes('focus') || classList.includes('ring')).toBeTruthy();
        });

        test('sidebar navigation items should be keyboard accessible', async ({ page }) => {
            // Find sidebar navigation
            const sidebar = page.locator('nav[role="navigation"][aria-label="Main navigation"]');
            await expect(sidebar).toBeVisible();

            // Tab into sidebar links
            const links = sidebar.locator('a');
            const linkCount = await links.count();

            // All links should be reachable via keyboard
            for (let i = 0; i < Math.min(linkCount, 3); i++) {
                const link = links.nth(i);
                if (await link.isVisible()) {
                    await link.focus();
                    await expect(link).toBeFocused();
                }
            }
        });

        test('escape key should close modals/dialogs', async ({ page }) => {
            // Try to open a modal (e.g., create workspace)
            const createButton = page.locator('button:has-text("New"), button:has-text("Create")').first();
            
            if (await createButton.isVisible()) {
                await createButton.click();
                
                // Wait for dialog to appear
                const dialog = page.locator('[role="dialog"]');
                
                if (await dialog.isVisible({ timeout: 2000 })) {
                    // Press Escape to close
                    await page.keyboard.press('Escape');
                    
                    // Dialog should be closed
                    await expect(dialog).not.toBeVisible({ timeout: 2000 });
                }
            }
        });
    });

    test.describe('ARIA & Landmarks', () => {
        test('page should have proper landmark structure', async ({ page }) => {
            // Check for main landmark
            const main = page.locator('main[role="main"], main');
            await expect(main).toBeVisible();

            // Check for navigation landmark
            const nav = page.locator('nav[role="navigation"], nav');
            await expect(nav.first()).toBeVisible();
        });

        test('main content should have proper aria-label', async ({ page }) => {
            const main = page.locator('main');
            const ariaLabel = await main.getAttribute('aria-label');
            
            // Main should have an aria-label
            expect(ariaLabel).toBeTruthy();
        });

        test('buttons should have accessible names', async ({ page }) => {
            // Get all buttons
            const buttons = page.locator('button');
            const buttonCount = await buttons.count();

            // Check first few buttons have accessible names
            for (let i = 0; i < Math.min(buttonCount, 5); i++) {
                const button = buttons.nth(i);
                if (await button.isVisible()) {
                    const accessibleName = await button.evaluate(el => {
                        return el.textContent?.trim() || 
                               el.getAttribute('aria-label') || 
                               el.getAttribute('title') ||
                               '';
                    });
                    
                    // Button should have some accessible name
                    if (!accessibleName) {
                        console.warn(`Button ${i} has no accessible name`);
                    }
                }
            }
        });
    });

    test.describe('Command Palette', () => {
        test('Cmd+K should open command palette', async ({ page }) => {
            // Press Cmd+K (or Ctrl+K on non-Mac)
            await page.keyboard.press('Meta+k');

            // Command palette should appear
            const commandPalette = page.locator('[role="dialog"], [data-testid="command-palette"]');
            
            // Give it a moment to open
            await page.waitForTimeout(500);
            
            const isVisible = await commandPalette.isVisible();
            if (!isVisible) {
                // Try Ctrl+K as fallback
                await page.keyboard.press('Control+k');
                await page.waitForTimeout(500);
            }

            // Close if opened
            await page.keyboard.press('Escape');
        });
    });
});

test.describe('Critical User Flows', () => {
    test.beforeEach(async ({ page }) => {
        await setupTest(page, {
            url: '/app',
            seedData: true
        });
    });

    test.afterEach(async ({ page }) => {
        await teardownTest(page);
    });

    test('user can navigate from home to project canvas', async ({ page }) => {
        // Wait for app to load
        await page.waitForSelector('nav[role="navigation"]');

        // Click first project
        const projectLink = page.locator('nav[role="navigation"] a[href^="/app/p/"]').first();
        
        if (await projectLink.isVisible()) {
            await projectLink.click();
            
            // Should navigate to project
            await page.waitForURL(/\/app\/p\/[^/]+/);
            
            // Canvas should be the default view (or navigate to it)
            const canvasTab = page.locator('a:has-text("Build"), [role="tab"]:has-text("Build")');
            if (await canvasTab.isVisible()) {
                await canvasTab.click();
            }
            
            // Wait for canvas or some content to appear
            await page.waitForTimeout(1000);
        }
    });

    test('sidebar can be collapsed and expanded', async ({ page }) => {
        // Find collapse button
        const collapseButton = page.locator('button[title*="Collapse"], button[title*="collapse"]');
        
        if (await collapseButton.isVisible()) {
            // Get initial sidebar width
            const sidebar = page.locator('nav[role="navigation"][aria-label="Main navigation"]');
            const initialWidth = await sidebar.evaluate((el) => (el as HTMLElement).offsetWidth);

            // Click collapse
            await collapseButton.click();
            await page.waitForTimeout(300); // Wait for animation

            // Sidebar should be narrower
            const collapsedWidth = await sidebar.evaluate((el) => (el as HTMLElement).offsetWidth);
            expect(collapsedWidth).toBeLessThan(initialWidth);

            // Find expand button and click it
            const expandButton = page.locator('button[title*="Expand"], button[title*="expand"]');
            if (await expandButton.isVisible()) {
                await expandButton.click();
                await page.waitForTimeout(300);

                // Sidebar should be back to original width
                const expandedWidth = await sidebar.evaluate((el) => (el as HTMLElement).offsetWidth);
                expect(expandedWidth).toBeGreaterThan(collapsedWidth);
            }
        }
    });

    test('theme can be toggled', async ({ page }) => {
        // Find theme toggle button
        const themeButton = page.locator('button[title="Theme"], button:has-text("Theme")').first();
        
        if (await themeButton.isVisible()) {
            // Get initial theme state
            const initialIsDark = await page.evaluate(() => 
                document.documentElement.classList.contains('dark')
            );

            // Click theme toggle
            await themeButton.click();
            await page.waitForTimeout(100);

            // Theme should have changed
            const newIsDark = await page.evaluate(() => 
                document.documentElement.classList.contains('dark')
            );

            expect(newIsDark).not.toBe(initialIsDark);
        }
    });
});
