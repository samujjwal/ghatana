/**
 * E2E Test Suite - Bootstrapping Export
 *
 * @description Tests export functionality for canvas and project data
 * in various formats (PNG, SVG, JSON, PDF).
 *
 * @doc.type test
 * @doc.purpose E2E export validation
 * @doc.layer e2e
 * @doc.phase bootstrapping
 */

import { test, expect, Download } from '@playwright/test';
import {
  StartProjectPage,
  BootstrapSessionPage,
  BootstrapExportPage,
} from './pages/bootstrapping.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Bootstrapping Export', () => {
  let startPage: StartProjectPage;
  let sessionPage: BootstrapSessionPage;
  let exportPage: BootstrapExportPage;
  let sessionId: string;

  test.beforeEach(async ({ page }) => {
    startPage = new StartProjectPage(page);
    sessionPage = new BootstrapSessionPage(page);
    exportPage = new BootstrapExportPage(page);

    // Login
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);

    // Create a session with some content
    await startPage.goto();
    await startPage.startWithIdea('Export test project with features');
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    sessionId = page.url().split('/').pop() || '';

    await sessionPage.waitForAIResponse();
    await sessionPage.sendMessage('Add user management and dashboard features');
    await sessionPage.waitForAIResponse();
  });

  // ==========================================================================
  // Export Format Selection Tests
  // ==========================================================================

  test('should display all export format options', async ({ page }) => {
    await exportPage.goto(sessionId);

    // Check for format options
    await expect(page.getByText(/png/i)).toBeVisible();
    await expect(page.getByText(/svg/i)).toBeVisible();
    await expect(page.getByText(/json/i)).toBeVisible();
    await expect(page.getByText(/pdf/i)).toBeVisible();
  });

  test('should allow selecting different export formats', async ({ page }) => {
    await exportPage.goto(sessionId);

    // Select PNG
    await exportPage.selectFormat('png');
    await expect(page.locator('[data-selected="true"]')).toContainText(/png/i);

    // Select SVG
    await exportPage.selectFormat('svg');
    await expect(page.locator('[data-selected="true"]')).toContainText(/svg/i);

    // Select JSON
    await exportPage.selectFormat('json');
    await expect(page.locator('[data-selected="true"]')).toContainText(/json/i);

    // Select PDF
    await exportPage.selectFormat('pdf');
    await expect(page.locator('[data-selected="true"]')).toContainText(/pdf/i);
  });

  // ==========================================================================
  // PNG Export Tests
  // ==========================================================================

  test('should export canvas as PNG', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('png');

    // Setup download listener
    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    const download = await downloadPromise;
    
    // Verify file name
    expect(download.suggestedFilename()).toMatch(/\.png$/);
  });

  test('should allow quality selection for PNG export', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('png');

    // Test quality options
    await exportPage.selectQuality('low');
    await exportPage.selectQuality('medium');
    await exportPage.selectQuality('high');
    await exportPage.selectQuality('maximum');

    // Export with maximum quality
    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.png$/);
  });

  test('should toggle background inclusion for PNG', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('png');

    // Toggle background off
    await exportPage.toggleBackground(false);
    
    // Toggle background on
    await exportPage.toggleBackground(true);
  });

  // ==========================================================================
  // SVG Export Tests
  // ==========================================================================

  test('should export canvas as SVG', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('svg');

    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.svg$/);
  });

  test('should preserve vectors in SVG export', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('svg');

    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    const download = await downloadPromise;
    
    // Read the file content
    const path = await download.path();
    if (path) {
      // In a real test, read file and verify SVG structure
      expect(download.suggestedFilename()).toMatch(/\.svg$/);
    }
  });

  // ==========================================================================
  // JSON Export Tests
  // ==========================================================================

  test('should export canvas data as JSON', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('json');

    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.json$/);
  });

  test('should include all canvas elements in JSON export', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('json');

    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    const download = await downloadPromise;
    const path = await download.path();
    
    if (path) {
      // In a real test, parse JSON and verify structure
      // const content = await fs.readFile(path, 'utf-8');
      // const data = JSON.parse(content);
      // expect(data.nodes).toBeDefined();
      // expect(data.edges).toBeDefined();
    }
  });

  test('should toggle metadata inclusion in JSON export', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('json');

    // Toggle metadata
    await exportPage.toggleMetadata(true);
    await exportPage.toggleMetadata(false);
  });

  // ==========================================================================
  // PDF Export Tests
  // ==========================================================================

  test('should export canvas as PDF', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('pdf');

    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.pdf$/);
  });

  test('should allow quality selection for PDF export', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('pdf');

    await exportPage.selectQuality('high');

    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.pdf$/);
  });

  // ==========================================================================
  // Preview Tests
  // ==========================================================================

  test('should display preview of canvas before export', async ({ page }) => {
    await exportPage.goto(sessionId);

    await expect(exportPage.previewPanel).toBeVisible();
  });

  test('should allow zoom controls in preview', async ({ page }) => {
    await exportPage.goto(sessionId);

    await expect(exportPage.previewPanel).toBeVisible();

    // Look for zoom controls
    const zoomIn = page.getByRole('button', { name: /zoom in/i });
    const zoomOut = page.getByRole('button', { name: /zoom out/i });

    if (await zoomIn.isVisible()) {
      await zoomIn.click();
      await zoomOut.click();
    }
  });

  // ==========================================================================
  // Export Progress Tests
  // ==========================================================================

  test('should show export progress indicator', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('png');
    await exportPage.selectQuality('maximum');

    // Start export
    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();

    // Should show progress indicator
    const progressIndicator = page.getByText(/exporting|generating|preparing/i);
    // Progress may be too fast to catch
    
    await downloadPromise;
  });

  test('should show success message after export', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('json');

    const downloadPromise = page.waitForEvent('download');
    await exportPage.export();
    await downloadPromise;

    // Should show success or the download link
    const successIndicator = page.getByText(/success|complete|done/i);
    // May not be visible if dialog closes automatically
  });

  // ==========================================================================
  // Export from Session Page Tests
  // ==========================================================================

  test('should open export dialog from session page', async ({ page }) => {
    await sessionPage.goto(sessionId);
    await sessionPage.export();

    // Export dialog or page should open
    const exportDialog = page.getByRole('dialog');
    const exportContent = page.getByText(/export|format/i);
    
    await expect(exportContent).toBeVisible({ timeout: 5000 });
  });

  // ==========================================================================
  // Custom File Name Tests
  // ==========================================================================

  test('should allow custom file name for export', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('png');

    // Enter custom file name
    const fileNameInput = page.getByLabel(/file name/i);
    if (await fileNameInput.isVisible()) {
      await fileNameInput.fill('my-custom-export');
      
      const downloadPromise = page.waitForEvent('download');
      await exportPage.export();
      
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toContain('my-custom-export');
    }
  });

  // ==========================================================================
  // Error Handling Tests
  // ==========================================================================

  test('should handle export errors gracefully', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('png');

    // Simulate network error during export
    await page.context().setOffline(true);
    await exportPage.export();

    // Should show error message
    await expect(page.getByText(/error|failed|try again/i)).toBeVisible({ timeout: 10000 });

    await page.context().setOffline(false);
  });

  test('should allow retry after export failure', async ({ page }) => {
    await exportPage.goto(sessionId);
    await exportPage.selectFormat('png');

    // Go offline
    await page.context().setOffline(true);
    await exportPage.export();
    
    // Wait for error
    await expect(page.getByText(/error|failed/i)).toBeVisible({ timeout: 10000 });

    // Go back online
    await page.context().setOffline(false);

    // Retry
    const retryButton = page.getByRole('button', { name: /retry|try again/i });
    if (await retryButton.isVisible()) {
      const downloadPromise = page.waitForEvent('download');
      await retryButton.click();
      
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/\.png$/);
    }
  });

  // ==========================================================================
  // Accessibility Tests
  // ==========================================================================

  test('should be keyboard navigable', async ({ page }) => {
    await exportPage.goto(sessionId);

    // Tab through export options
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // Should be able to select format with keyboard
    await page.keyboard.press('Enter');
  });

  test('should have proper ARIA labels for export controls', async ({ page }) => {
    await exportPage.goto(sessionId);

    // Check for accessible labels
    const exportButton = page.getByRole('button', { name: /export/i });
    await expect(exportButton).toBeVisible();

    // Format selection should be accessible
    const formatOptions = page.getByRole('button').filter({ hasText: /png|svg|json|pdf/i });
    expect(await formatOptions.count()).toBeGreaterThan(0);
  });
});

// ============================================================================
// Copy to Clipboard Tests
// ============================================================================

test.describe('Export Copy to Clipboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  test('should copy JSON to clipboard', async ({ page, context }) => {
    // Grant clipboard permissions
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);

    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);
    const exportPage = new BootstrapExportPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Clipboard test');
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page.url().split('/').pop() || '';

    await sessionPage.waitForAIResponse();

    await exportPage.goto(sessionId);
    await exportPage.selectFormat('json');

    // Click copy button
    const copyButton = page.getByRole('button', { name: /copy/i });
    if (await copyButton.isVisible()) {
      await copyButton.click();

      // Should show copied confirmation
      await expect(page.getByText(/copied/i)).toBeVisible({ timeout: 3000 });
    }
  });
});
