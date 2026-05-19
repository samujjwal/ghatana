/**
 * @fileoverview E2E tests for BuilderStudio workflow
 *
 * Tests the complete workflow of creating, editing, and previewing
 * BuilderDocuments in the Studio.
 *
 * @doc.type test
 * @doc.purpose BuilderStudio workflow E2E tests
 * @doc.layer studio
 */

import { expect, test } from '@playwright/test';

test.describe('BuilderStudio Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/studio/builder');
  });

  test('loads BuilderStudio with document list', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Builder Studio' })).toBeVisible();
    await expect(page.getByTestId('document-list')).toBeVisible();
  });

  test('creates a new BuilderDocument', async ({ page }) => {
    await page.getByTestId('create-document-button').click();
    
    // Fill in document details
    await page.getByTestId('document-name-input').fill('Test Document');
    await page.getByTestId('document-description-input').fill('Test description');
    
    await page.getByTestId('save-document-button').click();
    
    // Verify document was created
    await expect(page.getByText('Test Document')).toBeVisible();
  });

  test('edits an existing BuilderDocument', async ({ page }) => {
    // Select a document
    await page.getByTestId('document-item').first().click();
    
    // Add a component
    await page.getByTestId('add-component-button').click();
    await page.getByTestId('component-selector').selectOption('Button');
    await page.getByTestId('add-component-confirm').click();
    
    // Verify component was added
    await expect(page.getByTestId('component-node')).toBeVisible();
  });

  test('deletes a BuilderDocument', async ({ page }) => {
    // Select first document
    await page.getByTestId('document-item').first().click();
    
    // Click delete
    await page.getByTestId('delete-document-button').click();
    
    // Confirm deletion
    await page.getByTestId('confirm-delete-button').click();
    
    // Verify document was deleted
    await expect(page.getByText('Document deleted')).toBeVisible();
  });

  test('exports a BuilderDocument', async ({ page }) => {
    await page.getByTestId('document-item').first().click();
    await page.getByTestId('export-button').click();
    
    // Verify export dialog appears
    await expect(page.getByTestId('export-dialog')).toBeVisible();
    await page.getByTestId('export-json-button').click();
    
    // Verify download happened
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByTestId('export-confirm-button').click(),
    ]);
    
    expect(download.suggestedFilename()).toMatch(/\.json$/);
  });

  test('imports a BuilderDocument', async ({ page }) => {
    await page.getByTestId('import-button').click();
    
    // Upload a file
    const fileInput = page.getByTestId('import-file-input');
    await fileInput.setInputFiles('./fixtures/test-document.json');
    
    await page.getByTestId('import-confirm-button').click();
    
    // Verify import was successful
    await expect(page.getByText('Document imported successfully')).toBeVisible();
  });

  test('switches between canvas and code views', async ({ page }) => {
    await page.getByTestId('document-item').first().click();
    
    // Switch to canvas view
    await page.getByTestId('view-canvas').click();
    await expect(page.getByTestId('hybrid-canvas')).toBeVisible();
    
    // Switch to code view
    await page.getByTestId('view-code').click();
    await expect(page.getByTestId('code-editor')).toBeVisible();
  });

  test('persists document changes', async ({ page }) => {
    await page.getByTestId('document-item').first().click();
    
    // Make a change
    await page.getByTestId('document-name-input').fill('Updated Name');
    await page.getByTestId('save-button').click();
    
    // Reload page
    await page.reload();
    
    // Verify change persisted
    await expect(page.getByTestId('document-name-input')).toHaveValue('Updated Name');
  });
});
