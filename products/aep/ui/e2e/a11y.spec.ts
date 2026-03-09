/**
 * AEP UI Accessibility (a11y) Tests  @a11y
 *
 * Runs axe-core against the primary AEP Pipeline Builder pages to detect
 * WCAG 2.1 AA violations. Tagged with @a11y so they can be run in isolation
 * via `pnpm test:e2e:a11y`.
 *
 * @doc.type test
 * @doc.purpose axe-core accessibility audit for AEP UI pages
 * @doc.layer frontend
 */
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('AEP UI Accessibility @a11y', () => {
    test('pipeline builder page has no critical WCAG 2.1 AA violations', async ({ page }) => {
        await page.goto('/');

        const results = await new AxeBuilder({ page })
            .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
            .analyze();

        const critical = results.violations.filter(v => v.impact === 'critical');
        const serious = results.violations.filter(v => v.impact === 'serious');

        if (critical.length > 0 || serious.length > 0) {
            const summary = [...critical, ...serious].map(v =>
                `[${v.impact?.toUpperCase()}] ${v.id}: ${v.description}\n` +
                v.nodes.slice(0, 2).map(n => `  - ${n.target.join(', ')}`).join('\n')
            ).join('\n\n');
            expect(
                critical.length + serious.length,
                `Found ${critical.length} critical and ${serious.length} serious a11y violations:\n\n${summary}`
            ).toBe(0);
        }
    });

    test('pipeline builder toolbar is keyboard navigable @a11y', async ({ page }) => {
        await page.goto('/');

        await page.keyboard.press('Tab');
        const focused = await page.evaluate(() => document.activeElement?.tagName);
        expect(['BUTTON', 'A', 'INPUT', 'SELECT', '[role="button"]']).toContain(focused?.toUpperCase() ?? '');
    });

    test('pipeline builder has accessible landmarks @a11y', async ({ page }) => {
        await page.goto('/');

        const results = await new AxeBuilder({ page })
            .withRules(['landmark-one-main', 'region'])
            .analyze();

        const violations = results.violations.filter(v =>
            ['landmark-one-main', 'region'].includes(v.id)
        );

        expect(
            violations,
            `Missing required landmark regions: ${violations.map(v => v.id).join(', ')}`
        ).toHaveLength(0);
    });

    test('pipeline builder node context menus meet contrast requirements @a11y', async ({ page }) => {
        await page.goto('/');

        const results = await new AxeBuilder({ page })
            .withRules(['color-contrast'])
            .analyze();

        const contrastViolations = results.violations.filter(v => v.id === 'color-contrast');

        if (contrastViolations.length > 0) {
            const nodes = contrastViolations.flatMap(v => v.nodes).slice(0, 5);
            const detail = nodes.map(n => `  - ${n.target.join(', ')}: ${n.failureSummary}`).join('\n');
            expect(contrastViolations, `Color contrast violations:\n${detail}`).toHaveLength(0);
        }
    });

    test('pipeline canvas has aria-labels on interactive controls @a11y', async ({ page }) => {
        await page.goto('/');

        const results = await new AxeBuilder({ page })
            .withRules(['button-name', 'image-alt', 'label'])
            .analyze();

        expect(
            results.violations,
            `Interactive element a11y violations: ${results.violations.map(v => `${v.id}: ${v.description}`).join('; ')}`
        ).toHaveLength(0);
    });
});
