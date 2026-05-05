/**
 * Page Builder Critical Path E2E Tests
 *
 * Real browser coverage for add/select/delete/import interactions without
 * stubbing builder internals.
 *
 * @doc.type test
 * @doc.purpose Browser-level critical path for page builder interactions
 * @doc.layer product
 */

import { expect, test } from '@playwright/test';

const PROJECT_ID = 'test-project';
const CANVAS_URL = `/p/${PROJECT_ID}/canvas`;

test.describe('Page Builder Critical Path', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(CANVAS_URL);
    await page.waitForLoadState('networkidle');
  });

  test('adds and removes a page component through real canvas controls', async ({ page }) => {
    await expect(page.getByTestId('page-designer')).toBeVisible();

    await page.getByTestId('page-component-button').click();

    const designArea = page.getByTestId('page-design-area');
    await expect(designArea.getByRole('button', { name: /button/i })).toBeVisible();

    await designArea.getByRole('button', { name: /button/i }).click();
    await expect(page.getByTitle('Delete')).toBeVisible();

    await page.getByTitle('Delete').click();
    await expect(designArea.getByRole('button', { name: /button/i })).toHaveCount(0);
  });

  test('rejects malformed import payload in the builder import panel', async ({ page }) => {
    await expect(page.getByTestId('page-designer')).toBeVisible();

    await page.getByTestId('page-designer-import-btn').click();
    await page.getByTestId('page-designer-import-textarea').fill('{invalid json');
    await page.getByTestId('page-designer-import-confirm').click();

    await expect(page.getByText(/Invalid JSON - could not parse semantic model\./i)).toBeVisible();
  });
});
