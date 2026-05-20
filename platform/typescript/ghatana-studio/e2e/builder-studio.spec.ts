/**
 * @fileoverview E2E tests for the current Builder Studio workflow.
 *
 * These tests exercise the real route and stable accessible controls rather
 * than historical test ids from the retired BuilderStudio prototype.
 *
 * @doc.type test
 * @doc.purpose BuilderStudio workflow E2E tests
 * @doc.layer studio
 */

import { expect, test } from '@playwright/test';

async function createDocument(page: import('@playwright/test').Page) {
  await page.goto('/builder');
  await page.getByRole('button', { name: 'New Document', exact: true }).click();
  await expect(page.getByRole('heading', { name: /New Document/ })).toBeVisible();
}

test.describe('BuilderStudio Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/builder');
  });

  test('loads Builder Studio with document controls', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Builder Studio' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Documents' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Import' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'New Document', exact: true })).toBeVisible();
  });

  test('creates a new BuilderDocument and opens the authoring workspace', async ({ page }) => {
    await createDocument(page);

    await expect(page.getByRole('heading', { name: 'Components' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Tree', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Canvas' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Properties' })).toBeVisible();
    await expect(page.locator('h2').filter({ hasText: /^Validation$/ })).toBeVisible();
  });

  test('adds a palette component to the selected document', async ({ page }) => {
    await createDocument(page);

    const paletteButton = page
      .getByRole('button')
      .filter({ hasText: /button|card|input|container/i })
      .first();
    await expect(paletteButton).toBeVisible();
    await paletteButton.click();

    await expect(page.getByText(/button|card|input|container/i).first()).toBeVisible();
  });

  test('validates and exports a BuilderDocument', async ({ page }) => {
    await createDocument(page);

    await page.getByRole('button', { name: 'Validate', exact: true }).click();
    await expect(page.getByText(/valid|error|warning/i).first()).toBeVisible();

    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export' }).first().click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/New Document \d+\.json$/);
  });

  test('deletes a BuilderDocument from the document list', async ({ page }) => {
    await createDocument(page);

    await page.getByRole('button', { name: 'Delete' }).first().click();
    await expect(
      page.getByText('Select a document to view details or create a new document to get started.'),
    ).toBeVisible();
  });
});
