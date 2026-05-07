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
import {
  seedAuthenticatedSession,
  clearAuthenticatedSession,
  suppressViteErrorOverlay,
} from './auth-helpers';

type RouteAuditCase = {
    title: string;
    path: string;
    requiresAuth?: boolean;
};

const ROUTE_AUDIT_CASES: RouteAuditCase[] = [
    { title: 'login', path: '/login', requiresAuth: false },
    { title: 'monitoring dashboard', path: '/operate' },
    { title: 'HITL review queue', path: '/operate/reviews' },
    { title: 'pipeline list', path: '/build/pipelines' },
    { title: 'pipeline builder', path: '/build/pipelines/new' },
    { title: 'pattern studio', path: '/build/patterns' },
    { title: 'learning episodes', path: '/learn/episodes' },
    { title: 'memory explorer', path: '/learn/memory' },
    { title: 'governance dashboard', path: '/govern' },
    { title: 'agent registry', path: '/catalog/agents' },
    { title: 'workflow catalog', path: '/catalog/workflows' },
];

const AUTOMATED_A11Y_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const STRUCTURAL_RULES = ['landmark-one-main', 'region', 'button-name', 'image-alt', 'label', 'color-contrast'];

async function navigateForAudit(page: import('@playwright/test').Page, auditCase: RouteAuditCase): Promise<void> {
    await suppressViteErrorOverlay(page);

    if (auditCase.requiresAuth === false) {
        await clearAuthenticatedSession(page);
    } else {
        await seedAuthenticatedSession(page);
    }

    await page.goto(auditCase.path);
    await expect(page.locator('main')).toBeVisible();

    // Assert page identity before running axe to avoid auditing a login redirect or error state
    const pageTitle = await page.title();
    expect(pageTitle.toLowerCase()).toContain('aep');
}

async function analyzeCriticalAndSeriousViolations(page: import('@playwright/test').Page) {
    const results = await new AxeBuilder({ page })
        .withTags(AUTOMATED_A11Y_TAGS)
        .analyze();

    const critical = results.violations.filter(v => v.impact === 'critical');
    const serious = results.violations.filter(v => v.impact === 'serious');

    return { critical, serious };
}

async function analyzeStructuralRules(page: import('@playwright/test').Page) {
    return new AxeBuilder({ page })
        .withRules(STRUCTURAL_RULES)
        .analyze();
}

test.describe('AEP UI Accessibility @a11y', () => {
    for (const auditCase of ROUTE_AUDIT_CASES) {
        test(`${auditCase.title} page has no critical or serious WCAG 2.1 AA violations @a11y`, async ({ page }) => {
            await navigateForAudit(page, auditCase);

            const { critical, serious } = await analyzeCriticalAndSeriousViolations(page);

            if (critical.length > 0 || serious.length > 0) {
                const summary = [...critical, ...serious].map(v =>
                    `[${v.impact?.toUpperCase()}] ${v.id}: ${v.description}\n` +
                    v.nodes.slice(0, 2).map(n => `  - ${n.target.join(', ')}`).join('\n')
                ).join('\n\n');
                expect(
                    critical.length + serious.length,
                    `Found ${critical.length} critical and ${serious.length} serious a11y violations on ${auditCase.path}:\n\n${summary}`
                ).toBe(0);
            }
        });

        test(`${auditCase.title} page passes structural accessibility rules @a11y`, async ({ page }) => {
            await navigateForAudit(page, auditCase);

            const results = await analyzeStructuralRules(page);

            expect(
                results.violations,
                `Structural a11y violations on ${auditCase.path}: ${results.violations.map(v => `${v.id}: ${v.description}`).join('; ')}`
            ).toHaveLength(0);
        });

        test(`${auditCase.title} page supports keyboard entry into the main interaction model @a11y`, async ({ page }) => {
            await navigateForAudit(page, auditCase);

            await page.keyboard.press('Tab');
            const focused = await page.evaluate(() => {
                const active = document.activeElement;
                if (!active) {
                    return null;
                }

                return {
                    tag: active.tagName,
                    role: active.getAttribute('role'),
                    ariaLabel: active.getAttribute('aria-label'),
                };
            });

            expect(focused, `Expected a focusable element after keyboard entry on ${auditCase.path}`).not.toBeNull();
            expect(
                ['BUTTON', 'A', 'INPUT', 'SELECT', 'TEXTAREA'].includes(focused?.tag ?? '') || focused?.role === 'button',
                `Unexpected first focus target on ${auditCase.path}: ${JSON.stringify(focused)}`
            ).toBe(true);
        });
    }
});
