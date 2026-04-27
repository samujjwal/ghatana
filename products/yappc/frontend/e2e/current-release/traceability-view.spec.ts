/**
 * E2E tests – Traceability View
 *
 * Covers: traceability panel visible in canvas right panel, graph/matrix view
 * switching, artifact selection and detail panel, AI Analyze button, link mode.
 *
 * All tests are skipped (`test.skip`) because the traceability panel requires a
 * live canvas environment with seeded artifact data. Enable when the route is
 * reachable in CI with the ?panel=traceability query parameter.
 */

import { test, expect, type Page } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:7002';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Navigate to the canvas page with the traceability panel open. */
async function gotoTraceabilityPanel(
  page: Page,
  projectId = 'demo-project-1',
): Promise<void> {
  await page.goto(`${BASE_URL}/app/project/${projectId}/canvas?panel=traceability`);
  await page.waitForLoadState('networkidle');
}

// ---------------------------------------------------------------------------
// Suites
// ---------------------------------------------------------------------------

test.describe('Traceability Panel – graph view', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('traceability panel heading is visible', async ({ page }: { page: Page }) => {
    await gotoTraceabilityPanel(page);
    await expect(page.getByText('Traceability')).toBeVisible();
    await expect(page.getByText('Artifact dependencies & coverage')).toBeVisible();
  });

  test('graph view and matrix view toggle buttons are present', async ({ page }: { page: Page }) => {
    await gotoTraceabilityPanel(page);
    await expect(page.getByRole('button', { name: /graph view/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /matrix view/i })).toBeVisible();
  });

  test('can switch to matrix view', async ({ page }: { page: Page }) => {
    await gotoTraceabilityPanel(page);
    await page.getByRole('button', { name: /matrix view/i }).click();
    // Matrix shows a table with "From \ To" header
    await expect(page.getByText('From \\ To')).toBeVisible();
  });

  test('can switch back to graph view from matrix', async ({ page }: { page: Page }) => {
    await gotoTraceabilityPanel(page);
    await page.getByRole('button', { name: /matrix view/i }).click();
    await page.getByRole('button', { name: /graph view/i }).click();
    // Phase swimlane headings reappear in graph view
    await expect(page.getByText('INTENT')).toBeVisible();
  });
});

test.describe('Traceability Panel – artifact selection', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('selecting an artifact opens the detail panel', async ({ page }: { page: Page }) => {
    await gotoTraceabilityPanel(page);
    // Click the first artifact button in the graph
    const firstArtifact = page.locator('[data-testid="artifact-node"]').first();
    await firstArtifact.click();
    await expect(page.getByText('Links To')).toBeVisible();
    await expect(page.getByText('Links From')).toBeVisible();
  });

  test('clicking a selected artifact deselects it', async ({ page }: { page: Page }) => {
    await gotoTraceabilityPanel(page);
    const firstArtifact = page.locator('[data-testid="artifact-node"]').first();
    await firstArtifact.click();
    await firstArtifact.click();
    await expect(page.getByText('Links To')).not.toBeVisible();
  });
});

test.describe('Traceability Panel – refresh and AI analyze', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('refresh button is visible and clickable', async ({ page }: { page: Page }) => {
    await gotoTraceabilityPanel(page);
    const refreshButton = page.getByRole('button', { name: /refresh/i });
    await expect(refreshButton).toBeVisible();
    await refreshButton.click();
    // After refresh the panel still renders
    await expect(page.getByText('Traceability')).toBeVisible();
  });
});
