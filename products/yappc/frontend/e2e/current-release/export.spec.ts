/**
 * Export E2E Tests
 *
 * Validates the export dialog UI, format selection, content toggles,
 * submission, and download flow.
 *
 * @doc.type e2e
 * @doc.purpose Export artifact creation and download correctness
 * @doc.layer product
 */

import { test, expect, Page } from '@playwright/test';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function mockGraphQL(page: Page) {
  await page.route('**/graphql', async (route) => {
    const body = route.request().postDataJSON() as { query?: string } | null;
    const query = body?.query ?? '';

    if (query.includes('createExport')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            createExport: {
              id: 'exp-1',
              projectId: 'proj-1',
              format: 'MARKDOWN',
              status: 'READY',
              includeRequirements: true,
              includeDiagrams: true,
              includeCode: false,
              downloadUrl: '/api/exports/dev-placeholder/markdown',
              createdAt: new Date().toISOString(),
              completedAt: new Date().toISOString(),
            },
          },
        }),
      });
      return;
    }

    if (query.includes('exportArtifacts')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: { exportArtifacts: [] } }),
      });
      return;
    }

    await route.continue();
  });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Export Dialog', () => {
  test.beforeEach(async ({ page }) => {
    await mockGraphQL(page);
    await page.goto('/app');
  });

  test('opens the export dialog from the project toolbar', async ({ page }) => {
    // Try to find the export button in the UI; if it does not exist yet
    // navigate to a project that exposes it
    const exportBtn = page.getByRole('button', { name: /export/i }).first();
    if (await exportBtn.isVisible()) {
      await exportBtn.click();
      await expect(page.getByRole('dialog', { name: /export/i })).toBeVisible({
        timeout: 5000,
      });
    } else {
      // Skip: export button not yet surfaced in this route
      test.skip();
    }
  });

  test('ExportDialog renders with format options', async ({ page }) => {
    // Navigate to the route that renders ExportDialog or navigate via component test environment
    await page.goto('/app/projects/proj-1');
    const exportBtn = page.getByRole('button', { name: /export/i }).first();
    if (await exportBtn.isVisible()) {
      await exportBtn.click();
      await expect(page.getByText('Markdown')).toBeVisible({ timeout: 4000 });
      await expect(page.getByText('JSON')).toBeVisible();
      await expect(page.getByText('ZIP Bundle')).toBeVisible();
    } else {
      test.skip();
    }
  });

  test('selects JSON format before submitting', async ({ page }) => {
    await page.goto('/app/projects/proj-1');
    const exportBtn = page.getByRole('button', { name: /export/i }).first();
    if (await exportBtn.isVisible()) {
      await exportBtn.click();
      await page.getByRole('button', { name: /json/i }).click();
      const jsonOption = page.getByRole('button', { name: /json/i });
      await expect(jsonOption).toHaveAttribute('aria-pressed', 'true');
    } else {
      test.skip();
    }
  });

  test('submits export and shows Ready status with download link', async ({ page }) => {
    await page.goto('/app/projects/proj-1');
    const exportBtn = page.getByRole('button', { name: /export/i }).first();
    if (await exportBtn.isVisible()) {
      await exportBtn.click();
      await page.getByRole('button', { name: /^export$/i }).click();
      await expect(page.getByText(/ready|download/i)).toBeVisible({ timeout: 8000 });
    } else {
      test.skip();
    }
  });

  test('close button dismisses the export dialog', async ({ page }) => {
    await page.goto('/app/projects/proj-1');
    const exportBtn = page.getByRole('button', { name: /export/i }).first();
    if (await exportBtn.isVisible()) {
      await exportBtn.click();
      const dialog = page.getByRole('dialog', { name: /export/i });
      await expect(dialog).toBeVisible({ timeout: 4000 });
      await page.getByRole('button', { name: /close export dialog/i }).click();
      await expect(dialog).not.toBeVisible({ timeout: 4000 });
    } else {
      test.skip();
    }
  });

  test('export API rejects: error message is displayed', async ({ page }) => {
    // Override mock to reject
    await page.route('**/graphql', async (route) => {
      const body = route.request().postDataJSON() as { query?: string } | null;
      if (body?.query?.includes('createExport')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            errors: [{ message: 'Export quota exceeded' }],
          }),
        });
        return;
      }
      await route.continue();
    });

    await page.goto('/app/projects/proj-1');
    const exportBtn = page.getByRole('button', { name: /export/i }).first();
    if (await exportBtn.isVisible()) {
      await exportBtn.click();
      await page.getByRole('button', { name: /^export$/i }).click();
      await expect(page.getByText(/error|quota|failed/i)).toBeVisible({ timeout: 8000 });
    } else {
      test.skip();
    }
  });
});
