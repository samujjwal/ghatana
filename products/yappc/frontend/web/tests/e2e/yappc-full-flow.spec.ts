/**
 * YAPPC Full-Flow E2E Tests
 *
 * Tests mounted 8-phase project flow using project-scoped routes.
 *
 * @doc.type test
 * @doc.purpose YAPPC full-flow E2E tests
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { test, expect } from '@playwright/test';

const projectId = process.env.PLAYWRIGHT_PROJECT_ID ?? 'proj-1';
const phasePath = (phase: string): string => `/p/${projectId}/${phase}`;

async function gotoPhase(page: import('@playwright/test').Page, phase: string): Promise<void> {
  await page.goto(phasePath(phase));
  await page.waitForLoadState('networkidle');
  await expect(page.locator(`[data-testid="${phase}-cockpit"]`)).toBeVisible();
}

test.describe('YAPPC Full Flow', () => {
  test('intent phase mounts cockpit and primary action updates intent drawer URL', async ({ page }) => {
    await gotoPhase(page, 'intent');
    await expect(page.locator('[data-testid="phase-purpose"]')).toBeVisible();
    await expect(page.locator('[data-testid="primary-next-action"]')).toBeVisible();

    await page.click('[data-testid="define-requirements"]');
    await expect(page).toHaveURL(new RegExp(`/p/${projectId}/intent\\?drawer=idea`));
  });

  test('shape phase mounts cockpit with canvas as supporting surface', async ({ page }) => {
    await gotoPhase(page, 'shape');
    await expect(page.locator('[data-testid="canvas-container"]')).toBeVisible();
    await expect(page.locator('[data-testid="add-components"]')).toBeVisible();
  });

  test('validate phase shows gate summaries', async ({ page }) => {
    await gotoPhase(page, 'validate');
    await expect(page.locator('[data-testid="validation-status"]')).toBeVisible();
    await expect(page.locator('[data-testid="approval-gates"]')).toBeVisible();
  });

  test('generate phase shows codegen preview panels', async ({ page }) => {
    await gotoPhase(page, 'generate');
    await expect(page.locator('[data-testid="codegen-preview-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="generated-file-list"]')).toBeVisible();
  });

  test('run phase shows capability gates and run plan', async ({ page }) => {
    await gotoPhase(page, 'run');
    await expect(page.locator('[data-testid="capability-gates"]')).toBeVisible();
    await expect(page.locator('[data-testid="run-plan-panel"]')).toBeVisible();
  });

  test('observe phase shows preview and operations signals', async ({ page }) => {
    await gotoPhase(page, 'observe');
    await expect(page.locator('[data-testid="project-preview-iframe"]')).toBeVisible();
    await expect(page.locator('[data-testid="metrics-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="incidents-panel"]')).toBeVisible();
  });

  test('learn and evolve phases show retrospective and roadmap panels', async ({ page }) => {
    await gotoPhase(page, 'learn');
    await expect(page.locator('[data-testid="retrospective-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="reusable-patterns"]')).toBeVisible();

    await gotoPhase(page, 'evolve');
    await expect(page.locator('[data-testid="roadmap-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="backlog-panel"]')).toBeVisible();
  });

  test('complete mounted project lifecycle route sweep', async ({ page }) => {
    const phases = [
      { phase: 'intent', primaryAction: 'define-requirements' },
      { phase: 'shape', primaryAction: 'add-components' },
      { phase: 'validate', primaryAction: 'approve-changes' },
      { phase: 'generate', primaryAction: 'generate-code' },
      { phase: 'run', primaryAction: 'check-readiness' },
      { phase: 'observe', primaryAction: 'view-metrics' },
      { phase: 'learn', primaryAction: 'capture-learnings' },
      { phase: 'evolve', primaryAction: 'plan-next-cycle' },
    ] as const;

    for (const { phase, primaryAction } of phases) {
      await gotoPhase(page, phase);
      await expect(page.locator('[data-testid="phase-purpose"]')).toBeVisible();
      await expect(page.locator(`[data-testid="${primaryAction}"]`)).toBeVisible();
    }
  });
});

test.describe('YAPPC Full Flow Accessibility', () => {
  test('intent cockpit exposes region semantics and label', async ({ page }) => {
    await gotoPhase(page, 'intent');
    const cockpit = page.locator('[data-testid="intent-cockpit"]');
    await expect(cockpit).toHaveAttribute('role', 'region');
    await expect(cockpit).toHaveAttribute('aria-label', /intent cockpit/i);
  });

  test('keyboard can focus primary phase action', async ({ page }) => {
    await gotoPhase(page, 'shape');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    const activeDataTestId = await page.evaluate(() => {
      const element = document.activeElement as HTMLElement | null;
      return element?.getAttribute('data-testid') ?? null;
    });
    expect(activeDataTestId).not.toBeNull();
  });
});
