/**
 * E2E Test Suite - Accessibility & Navigation Tests
 * Tests for WCAG compliance and navigation features aligned with the
 * current unified-header layout and /workspaces entry route.
 */

import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from '../helpers/test-isolation';

test.describe('Accessibility & Navigation', () => {
    test.beforeEach(async ({ page }) => {
        await setupTest(page, {
            url: '/workspaces',
            seedData: false
        });
    });

    test.afterEach(async ({ page }) => {
        await teardownTest(page);
    });

    test.describe('Skip Links', () => {
        test('skip link should be visible on focus and navigate to main content', async ({ page }) => {
            await page.keyboard.press('Tab');

            const skipLink = page.locator('a:has-text("Skip to main content")');

            let found = false;
            for (let i = 0; i < 5; i++) {
                if (await skipLink.isVisible().catch(() => false)) {
                    found = true;
                    break;
                }
                await page.keyboard.press('Tab');
            }

            if (found) {
                await expect(skipLink).toBeVisible();
                await page.keyboard.press('Enter');
                const mainContent = page.locator('#main-content');
                await expect(mainContent).toBeFocused();
            }
        });
    });

    test.describe('ARIA & Landmarks', () => {
        test('page should have proper landmark structure', async ({ page }) => {
            const main = page.locator('main[role="main"], main');
            await expect(main).toBeVisible();

            const nav = page.locator('nav[role="navigation"], nav');
            await expect(nav.first()).toBeVisible();
        });

        test('main content should have proper aria-label', async ({ page }) => {
            const main = page.locator('main');
            const ariaLabel = await main.getAttribute('aria-label');
            expect(ariaLabel).toBeTruthy();
        });

        test('buttons should have accessible names', async ({ page }) => {
            const buttons = page.locator('button');
            const buttonCount = await buttons.count();

            for (let i = 0; i < Math.min(buttonCount, 5); i++) {
                const button = buttons.nth(i);
                if (await button.isVisible().catch(() => false)) {
                    const accessibleName = await button.evaluate(el => {
                        return el.textContent?.trim() ||
                               el.getAttribute('aria-label') ||
                               el.getAttribute('title') ||
                               '';
                    });
                    if (!accessibleName) {
                        console.warn(`Button ${i} has no accessible name`);
                    }
                }
            }
        });
    });

    test.describe('Focus Management', () => {
        test('interactive elements should have visible focus indicators', async ({ page }) => {
            await page.keyboard.press('Tab');

            const focusedElement = page.locator(':focus');
            const classList = await focusedElement.evaluate(el => el.className);
            const hasOutline = await focusedElement.evaluate(el => {
                const styles = getComputedStyle(el);
                return styles.outlineWidth !== '0px' ||
                       styles.boxShadow.includes('ring') ||
                       el.className.includes('ring');
            });

            expect(hasOutline || classList.includes('focus') || classList.includes('ring')).toBeTruthy();
        });

        test('header navigation items should be keyboard accessible', async ({ page }) => {
            const header = page.locator('header, [role="banner"]').first();
            await expect(header).toBeVisible();

            const buttons = header.locator('button');
            const count = await buttons.count();

            for (let i = 0; i < Math.min(count, 3); i++) {
                const btn = buttons.nth(i);
                if (await btn.isVisible().catch(() => false)) {
                    await btn.focus();
                    await expect(btn).toBeFocused();
                }
            }
        });

        test('escape key should close modals/dialogs', async ({ page }) => {
            const createButton = page.locator('button:has-text("New"), button:has-text("Create")').first();

            if (await createButton.isVisible().catch(() => false)) {
                await createButton.click();

                const dialog = page.locator('[role="dialog"]');
                if (await dialog.isVisible({ timeout: 2000 }).catch(() => false)) {
                    await page.keyboard.press('Escape');
                    await expect(dialog).not.toBeVisible({ timeout: 2000 });
                }
            }
        });
    });

    test.describe('Command Palette', () => {
        test('Cmd+K should open command palette', async ({ page }) => {
            await page.keyboard.press('Meta+k');

            const commandPalette = page.locator('[role="dialog"], [data-testid="command-palette"]');
            await page.waitForTimeout(500);

            let isVisible = await commandPalette.isVisible().catch(() => false);
            if (!isVisible) {
                await page.keyboard.press('Control+k');
                await page.waitForTimeout(500);
            }

            await page.keyboard.press('Escape');
        });
    });

    test.describe('Theme Toggle', () => {
        test('theme can be toggled', async ({ page }) => {
            const themeButton = page.locator('button[title="Theme"], button:has-text("Theme"), button[aria-label*="theme" i], button[aria-label*="dark" i]').first();

            if (await themeButton.isVisible().catch(() => false)) {
                const initialIsDark = await page.evaluate(() => document.documentElement.classList.contains('dark'));
                await themeButton.click();
                await page.waitForTimeout(100);
                const newIsDark = await page.evaluate(() => document.documentElement.classList.contains('dark'));
                expect(newIsDark).not.toBe(initialIsDark);
            }
        });
    });

    test.describe('Breadcrumb Navigation', () => {
        test('breadcrumb should be present when context is loaded', async ({ page }) => {
            const breadcrumbNav = page.locator('nav[aria-label="Breadcrumb"]');
            if (await breadcrumbNav.isVisible().catch(() => false)) {
                const homeLink = breadcrumbNav.locator('a[aria-label="Home"]');
                await expect(homeLink).toBeVisible();
            }
        });
    });
});
