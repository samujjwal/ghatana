import { test, expect } from '@playwright/test';

/**
 * DevSecOps Persona Dashboards & Canvas Templates E2E Tests
 *
 * Scope:
 * - Persona dashboard journeys from the main DevSecOps dashboard
 * - Focus area navigation into reports, phase detail, and settings
 * - Templates → Canvas flows using the `template` query param
 */

// Persona journeys

test.describe('DevSecOps Persona Dashboards', () => {
    test.describe.configure({ mode: 'serial' });

    test.beforeEach(async ({ page }) => {
        await page.goto('/devsecops');
        await page.waitForLoadState('networkidle');
    });

    test('should display persona dashboard cards on main DevSecOps dashboard', async ({ page }) => {
        const personaCards = page.locator('[data-testid="persona-card"]');
        const count = await personaCards.count();
        expect(count).toBeGreaterThan(0);
    });

    test('should navigate to persona dashboard on card click', async ({ page }) => {
        const firstPersona = page.locator('[data-testid="persona-card"]').first();
        await firstPersona.click();

        await page.waitForURL(/\/devsecops\/persona\//);
        expect(page.url()).toContain('/devsecops/persona/');
    });

    test('CISO persona focus areas should deep-link into reports', async ({ page }) => {
        await page.goto('/devsecops/persona/ciso-executive-overview');
        await page.waitForLoadState('networkidle');

        // Basic header sanity check
        await expect(page.getByText('Key Performance Indicators')).toBeVisible();
        await expect(page.getByText('Focus Areas')).toBeVisible();

        const focusAreas = page.locator('[data-testid="focus-area-item"]');

        // Risk posture trends → Executive Summary report
        await focusAreas.nth(0).click();
        await page.waitForURL(/\/devsecops\/reports\/executive/);
        await expect(page.getByText('Executive Summary')).toBeVisible();

        // Navigate back and try Security & Compliance
        await page.goto('/devsecops/persona/ciso-executive-overview');
        await focusAreas.nth(1).click();
        await page.waitForURL(/\/devsecops\/reports\/security/);
        await expect(page.getByText('Security & Compliance')).toBeVisible();

        // Navigate back and try Operational Health
        await page.goto('/devsecops/persona/ciso-executive-overview');
        await focusAreas.nth(2).click();
        await page.waitForURL(/\/devsecops\/reports\/operations/);
        await expect(page.getByText('Operational Health')).toBeVisible();
    });

    test('DevSecOps Engineer persona focus areas should deep-link into phase detail views', async ({ page }) => {
        await page.goto('/devsecops/persona/devsecops-engineer-operations');
        await page.waitForLoadState('networkidle');

        const focusAreas = page.locator('[data-testid="focus-area-item"]');

        // Pipeline throughput & failures → Development phase
        await focusAreas.nth(0).click();
        await page.waitForURL(/\/devsecops\/phase\/development/);
        await expect(page.getByText(/Development/i)).toBeVisible();

        // Security gate adherence → Security phase
        await page.goto('/devsecops/persona/devsecops-engineer-operations');
        await focusAreas.nth(1).click();
        await page.waitForURL(/\/devsecops\/phase\/security/);
        await expect(page.getByText(/Security/i)).toBeVisible();

        // Hotspot remediation velocity → Development phase again
        await page.goto('/devsecops/persona/devsecops-engineer-operations');
        await focusAreas.nth(2).click();
        await page.waitForURL(/\/devsecops\/phase\/development/);
    });

    test('Compliance Officer persona focus areas should deep-link into settings and reports', async ({ page }) => {
        await page.goto('/devsecops/persona/compliance-officer-assurance');
        await page.waitForLoadState('networkidle');

        const focusAreas = page.locator('[data-testid="focus-area-item"]');

        // Control assessment cycle → Settings
        await focusAreas.nth(0).click();
        await page.waitForURL('/devsecops/settings');
        await expect(page.getByRole('heading', { name: /Settings & Governance/i })).toBeVisible();

        // Evidence collection progress → Security & Compliance report
        await page.goto('/devsecops/persona/compliance-officer-assurance');
        await focusAreas.nth(1).click();
        await page.waitForURL(/\/devsecops\/reports\/security/);

        // Exception backlog visibility → Security & Compliance report
        await page.goto('/devsecops/persona/compliance-officer-assurance');
        await focusAreas.nth(2).click();
        await page.waitForURL(/\/devsecops\/reports\/security/);
    });
});

// Canvas template flows

test.describe('DevSecOps Canvas Templates Flow', () => {
    const templateIds = ['executive', 'mvp', 'enterprise', 'security-first'] as const;

    test.beforeEach(async ({ page }) => {
        await page.goto('/devsecops/templates');
        await page.waitForLoadState('networkidle');
    });

    test('should navigate to canvas with template query when applying templates', async ({ page }) => {
        const accordions = page.locator('[data-testid="template-accordion"]');

        for (let i = 0; i < templateIds.length; i++) {
            const applyButton = accordions
                .nth(i)
                .getByRole('button', { name: 'Apply Template' });

            await applyButton.click();

            // Canvas should open with template query param
            await page.waitForURL(new RegExp(`/devsecops/canvas\\?template=${templateIds[i]}`));
            await expect(page.getByText(/DevSecOps Canvas -/)).toBeVisible();

            // Go back to templates page for next iteration
            if (i < templateIds.length - 1) {
                await page.goto('/devsecops/templates');
                await page.waitForLoadState('networkidle');
            }
        }
    });

    test('should allow loading templates from within the canvas dialog', async ({ page }) => {
        // Start directly on canvas with no template
        await page.goto('/devsecops/canvas');
        await page.waitForLoadState('networkidle');

        // Open Templates dialog from canvas header
        const templatesButton = page.getByRole('button', { name: /Templates/i });
        await templatesButton.click();

        const dialog = page.getByRole('dialog', { name: /Load Template/i });
        await expect(dialog).toBeVisible();

        // Click first "Load Template" button
        const loadButton = dialog.getByRole('button', { name: /Load Template/i }).first();
        await loadButton.click();

        // Canvas should still be visible after loading template
        await expect(page.getByText(/DevSecOps Canvas -/)).toBeVisible();
    });
});
