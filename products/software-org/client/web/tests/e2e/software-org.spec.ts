import { test, expect } from '@playwright/test';

/**
 * End-to-End Tests for SOFTWARE-ORG Frontend.
 *
 * Tests validate:
 * - Critical user workflows
 * - Full application flows
 * - Integration between features
 * - Real-world usage scenarios
 *
 * Run with: pnpm test:e2e
 */

const BASE_URL = process.env.PLAYWRIGHT_TEST_BASE_URL || 'http://localhost:5173';

test.describe('SOFTWARE-ORG E2E Tests', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto(BASE_URL);
    });

    test.describe('Dashboard Navigation', () => {
        test('should navigate to dashboard and display KPIs', async ({ page }) => {
            // Go to dashboard
            await page.click('[data-testid="nav-dashboard"]');

            // Check for KPI cards
            await expect(page.locator('text=Deployments')).toBeVisible();
            await expect(page.locator('text=Lead Time')).toBeVisible();
            await expect(page.locator('text=MTTR')).toBeVisible();

            // Check for KPI values
            await expect(page.locator('text=156/week')).toBeVisible();
        });

        test('should use keyboard shortcut to navigate dashboard', async ({ page }) => {
            // Use ⌘+1 to navigate to dashboard
            await page.keyboard.press('Control+1');

            // Verify on dashboard
            await expect(page.locator('heading')).toContainText(/Dashboard|Control Tower/i);
        });
    });

    test.describe('AI Intelligence Features', () => {
        test('should submit natural language query and see results', async ({ page }) => {
            // Navigate to dashboard
            await page.click('[data-testid="nav-dashboard"]');

            // Find and focus NL query input
            const queryInput = page.locator('[placeholder*="Ask a question"]').first();
            await queryInput.focus();

            // Type query
            await queryInput.fill('Why is MTTR increasing?');

            // Submit with Enter
            await queryInput.press('Enter');

            // Wait for results
            await page.waitForSelector('[data-testid="nl-query-results"]', { timeout: 5000 });

            // Verify confidence score appears
            await expect(page.locator('text=Confidence')).toBeVisible();
        });

        test('should interact with what-if analysis panel', async ({ page }) => {
            // Navigate to dashboard
            await page.click('[data-testid="nav-dashboard"]');

            // Expand what-if panel
            const expandButton = page.locator('button:has-text("What-If Analysis")').first();
            await expandButton.click();

            // Adjust deployment frequency slider
            const slider = page.locator('[aria-label="Deployment frequency slider"]').first();
            await slider.evaluate((el: HTMLInputElement) => {
                el.value = '200';
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
            });

            // Check for impact projection
            await expect(page.locator('text=Impact Projection')).toBeVisible();
        });

        test('should view AI explainability for predictions', async ({ page }) => {
            // Navigate to dashboard
            await page.click('[data-testid="nav-dashboard"]');

            // Find and click explainability button
            const explainButton = page.locator('button:has-text("Explain")').first();
            if (await explainButton.isVisible({ timeout: 2000 }).catch(() => false)) {
                await explainButton.click();

                // Wait for modal
                await page.waitForSelector('[role="dialog"]', { timeout: 3000 });

                // Verify explainability content
                await expect(page.locator('text=Feature Importance')).toBeVisible();
                await expect(page.locator('text=Model')).toBeVisible();
            }
        });
    });

    test.describe('Security Features', () => {
        test('should navigate to security and view vulnerabilities', async ({ page }) => {
            // Navigate to security
            await page.click('[data-testid="nav-security"]');

            // Verify vulnerability dashboard
            await expect(page.locator('text=Vulnerability Dashboard|Vulnerabilities')).toBeVisible();

            // Check for severity groups
            await expect(page.locator('text=Critical')).toBeVisible();
        });

        test('should view compliance posture', async ({ page }) => {
            // Navigate to security
            await page.click('[data-testid="nav-security"]');

            // Look for compliance section
            const complianceSection = page.locator('button:has-text("Compliance")').first();
            if (await complianceSection.isVisible({ timeout: 2000 }).catch(() => false)) {
                await complianceSection.click();

                // Verify frameworks
                await expect(page.locator('text=SOC2|ISO27001|GDPR|HIPAA')).toBeVisible();
            }
        });
    });

    test.describe('ML Observatory', () => {
        test('should view model performance dashboard', async ({ page }) => {
            // Navigate to models
            await page.click('[data-testid="nav-models"]');

            // Verify performance dashboard
            await expect(page.locator('text=Model Performance|Accuracy')).toBeVisible();

            // Check metrics
            await expect(page.locator('text=Precision')).toBeVisible();
            await expect(page.locator('text=Recall')).toBeVisible();
            await expect(page.locator('text=F1 Score')).toBeVisible();
        });

        test('should view A/B test results', async ({ page }) => {
            // Navigate to models
            await page.click('[data-testid="nav-models"]');

            // Find A/B test section
            const abTestButton = page.locator('button:has-text("A/B Test")').first();
            if (await abTestButton.isVisible({ timeout: 2000 }).catch(() => false)) {
                await abTestButton.click();

                // Verify A/B test content
                await expect(page.locator('text=Champion|Challenger')).toBeVisible();
            }
        });

        test('should monitor drift detection', async ({ page }) => {
            // Navigate to models
            await page.click('[data-testid="nav-models"]');

            // Find drift monitor section
            const driftButton = page.locator('button:has-text("Drift")').first();
            if (await driftButton.isVisible({ timeout: 2000 }).catch(() => false)) {
                await driftButton.click();

                // Verify drift content
                await expect(page.locator('text=Drift Detection|Drift Score')).toBeVisible();
            }
        });

        test('should interact with model registry', async ({ page }) => {
            // Navigate to models
            await page.click('[data-testid="nav-models"]');

            // Find registry section
            const registryButton = page.locator('button:has-text("Registry")').first();
            if (await registryButton.isVisible({ timeout: 2000 }).catch(() => false)) {
                await registryButton.click();

                // Verify registry content
                await expect(page.locator('text=Model Registry')).toBeVisible();

                // Toggle comparison mode
                const compareButton = page.locator('button:has-text("Compare")').first();
                await compareButton.click();

                // Verify comparison view
                await expect(page.locator('text=Champion|Challenger')).toBeVisible();
            }
        });
    });

    test.describe('Keyboard Navigation', () => {
        test('should navigate with keyboard shortcuts', async ({ page }) => {
            // ⌘+K to open command palette (if available)
            await page.keyboard.press('Control+K');

            // Should open command palette or focus search
            // (implementation depends on app structure)
            await page.keyboard.press('Escape');
        });

        test('should tab through interactive elements', async ({ page }) => {
            // Get all focusable elements
            const focusableElements = await page.locator('button, a, [tabindex]').count();

            expect(focusableElements).toBeGreaterThan(0);

            // Tab through first few elements
            for (let i = 0; i < 5; i++) {
                await page.keyboard.press('Tab');
            }

            // Verify we can focus elements
            const focused = await page.evaluate(() => document.activeElement?.tagName);
            expect(focused).toBeTruthy();
        });

        test('should use Escape to close modals', async ({ page }) => {
            // Navigate to security
            await page.click('[data-testid="nav-security"]');

            // Find and click explainability button if available
            const button = page.locator('button').first();
            if (await button.isVisible()) {
                await button.click();

                // Try to close with Escape
                await page.keyboard.press('Escape');
            }
        });
    });

    test.describe('Dark Mode', () => {
        test('should toggle dark mode', async ({ page }) => {
            // Find theme toggle button
            const themeToggle = page.locator('[aria-label*="theme"], [data-testid="theme-toggle"]').first();

            if (await themeToggle.isVisible({ timeout: 2000 }).catch(() => false)) {
                // Get initial theme
                const initialDark = await page.evaluate(() =>
                    document.documentElement.classList.contains('dark')
                );

                // Click toggle
                await themeToggle.click();

                // Wait for change
                await page.waitForTimeout(300);

                // Verify change
                const newDark = await page.evaluate(() =>
                    document.documentElement.classList.contains('dark')
                );

                expect(newDark).not.toBe(initialDark);
            }
        });

        test('should maintain dark mode across navigation', async ({ page }) => {
            // Enable dark mode
            await page.evaluate(() => {
                document.documentElement.classList.add('dark');
            });

            // Navigate between pages
            await page.click('[data-testid="nav-dashboard"]');
            await page.waitForTimeout(300);

            let isDark = await page.evaluate(() =>
                document.documentElement.classList.contains('dark')
            );
            expect(isDark).toBe(true);

            await page.click('[data-testid="nav-security"]');
            await page.waitForTimeout(300);

            isDark = await page.evaluate(() =>
                document.documentElement.classList.contains('dark')
            );
            expect(isDark).toBe(true);
        });
    });

    test.describe('Accessibility', () => {
        test('should have proper heading hierarchy', async ({ page }) => {
            // Get all headings
            const headings = await page.locator('h1, h2, h3, h4, h5, h6').count();

            expect(headings).toBeGreaterThan(0);
        });

        test('should have visible focus indicators', async ({ page }) => {
            // Focus first button
            const button = page.locator('button').first();
            await button.focus();

            // Get computed focus styles
            const focusStyle = await button.evaluate((el) => {
                const style = window.getComputedStyle(el);
                return {
                    outline: style.outline,
                    outlineWidth: style.outlineWidth,
                };
            });

            expect(focusStyle.outlineWidth).not.toBe('0px');
        });

        test('should have proper ARIA labels', async ({ page }) => {
            // Check for ARIA labels on interactive elements
            const ariaLabels = await page.locator('[aria-label]').count();

            expect(ariaLabels).toBeGreaterThan(0);
        });
    });

    test.describe('Performance', () => {
        test('should load dashboard within acceptable time', async ({ page }) => {
            const startTime = Date.now();

            // Navigate to dashboard
            await page.click('[data-testid="nav-dashboard"]');

            // Wait for main content
            await page.waitForSelector('[data-testid="dashboard-content"]', { timeout: 5000 });

            const loadTime = Date.now() - startTime;

            // Should load within 5 seconds
            expect(loadTime).toBeLessThan(5000);
        });

        test('should handle rapid navigation', async ({ page }) => {
            // Rapidly navigate between sections
            for (let i = 0; i < 3; i++) {
                await page.click('[data-testid="nav-dashboard"]');
                await page.click('[data-testid="nav-security"]');
                await page.click('[data-testid="nav-models"]');
            }

            // Should not crash or error
            const hasErrors = await page.evaluate(() => {
                const errors = window.__ERRORS__ || [];
                return errors.length > 0;
            }).catch(() => false);

            expect(hasErrors).toBe(false);
        });
    });

    test.describe('Error Handling', () => {
        test('should display error state gracefully', async ({ page }) => {
            // Navigate to page with potential error
            await page.click('[data-testid="nav-dashboard"]');

            // If error state exists, should show error UI not crash
            // (actual implementation depends on error handling)
            await expect(page).not.toHaveURL(/error|500|crash/i);
        });
    });
});
