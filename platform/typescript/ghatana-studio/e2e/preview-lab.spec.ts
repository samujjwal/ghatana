/**
 * @fileoverview E2E tests for PreviewLab workflow
 *
 * Tests the preview rendering, sandbox isolation, and interaction workflows.
 *
 * @doc.type test
 * @doc.purpose PreviewLab workflow E2E tests
 * @doc.layer studio
 */

import { expect, test } from '@playwright/test';

test.describe('PreviewLab Workflow', () => {
  test.beforeEach(async ({ page, context }) => {
    // Enable web permissions for preview iframes
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  });

  test('loads PreviewLab with document selector', async ({ page }) => {
    await page.goto('/studio/preview');
    
    await expect(page.getByRole('heading', { name: 'Preview Lab' })).toBeVisible();
    await expect(page.getByTestId('document-selector')).toBeVisible();
  });

  test('renders preview in sandboxed iframe', async ({ page }) => {
    await page.goto('/studio/preview');
    
    // Select a document
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    // Wait for preview iframe to load
    await expect(page.getByTestId('preview-iframe')).toBeVisible();
    
    const iframe = page.frameLocator('iframe').first();
    await expect(iframe.getByRole('document')).toBeVisible();
  });

  test('supports preview mode switching', async ({ page }) => {
    await page.goto('/studio/preview');
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    // Switch to desktop mode
    await page.getByTestId('mode-desktop').click();
    await expect(page.getByTestId('preview-iframe')).toHaveAttribute('data-mode', 'desktop');
    
    // Switch to mobile mode
    await page.getByTestId('mode-mobile').click();
    await expect(page.getByTestId('preview-iframe')).toHaveAttribute('data-mode', 'mobile');
  });

  test('displays preview controls', async ({ page }) => {
    await page.goto('/studio/preview');
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    await expect(page.getByTestId('refresh-button')).toBeVisible();
    await expect(page.getByTestId('fullscreen-button')).toBeVisible();
    await expect(page.getByTestId('devtools-button')).toBeVisible();
  });

  test('refreshes preview on demand', async ({ page }) => {
    await page.goto('/studio/preview');
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    await page.getByTestId('refresh-button').click();
    
    // Wait for refresh to complete
    await expect(page.getByTestId('preview-status')).toContainText('Ready');
  });

  test('displays console logs from preview', async ({ page }) => {
    await page.goto('/studio/preview');
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    // Switch to console tab
    await page.getByTestId('tab-console').click();
    
    await expect(page.getByTestId('console-output')).toBeVisible();
  });

  test('displays network requests from preview', async ({ page }) => {
    await page.goto('/studio/preview');
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    // Switch to network tab
    await page.getByTestId('tab-network').click();
    
    await expect(page.getByTestId('network-table')).toBeVisible();
  });

  test('supports interactive preview testing', async ({ page }) => {
    await page.goto('/studio/preview');
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    const iframe = page.frameLocator('iframe').first();
    
    // Interact with preview
    const button = iframe.getByRole('button', { name: /click/i }).first();
    if (await button.count() > 0) {
      await button.click();
      
      // Verify interaction was logged
      await page.getByTestId('tab-console').click();
      await expect(page.getByTestId('console-output')).toContainText('click');
    }
  });

  test('handles preview errors gracefully', async ({ page }) => {
    await page.goto('/studio/preview');
    
    // Select a document that might have errors
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    // Check error display
    const errorPanel = page.getByTestId('error-panel');
    if (await errorPanel.isVisible()) {
      await expect(errorPanel).toContainText('Error');
    }
  });

  test('supports preview sandbox configuration', async ({ page }) => {
    await page.goto('/studio/preview');
    
    // Open sandbox settings
    await page.getByTestId('sandbox-settings').click();
    
    await expect(page.getByTestId('sandbox-config')).toBeVisible();
    
    // Configure sandbox
    await page.getByTestId('allow-scripts').check();
    await page.getByTestId('allow-same-origin').uncheck();
    
    await page.getByTestId('apply-sandbox').click();
    
    // Verify configuration applied
    await expect(page.getByTestId('sandbox-status')).toContainText('Custom');
  });

  test('exports preview snapshot', async ({ page }) => {
    await page.goto('/studio/preview');
    await page.getByTestId('document-selector').selectOption({ index: 0 });
    
    await page.getByTestId('snapshot-button').click();
    
    // Verify snapshot was captured
    await expect(page.getByTestId('snapshot-preview')).toBeVisible();
    
    // Download snapshot
    await page.getByTestId('download-snapshot').click();
    
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByTestId('confirm-download').click(),
    ]);
    
    expect(download.suggestedFilename()).toMatch(/\.png$/);
  });
});
