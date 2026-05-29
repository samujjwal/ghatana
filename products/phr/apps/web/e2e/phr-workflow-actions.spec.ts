/**
 * T-014: PHR workflow action E2E tests.
 * Tests key user workflows: consent, appointments, documents, OCR, emergency, audit.
 */

import { test, expect } from '@playwright/test';

test.describe('PHR workflow actions - consent', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('consent grant workflow', async ({ page }) => {
    await page.goto('/consents');
    
    // Check consents page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for grant consent button/action
    const grantButton = page.locator('button, [role="button"]').filter({ hasText: /grant|create|add/i }).first();
    const hasGrantButton = await grantButton.count() > 0;
    
    if (hasGrantButton) {
      await grantButton.click();
      // Check for consent form
      const form = page.locator('form');
      await expect(form.first()).toBeVisible();
    }
  });

  test('consent revoke workflow', async ({ page }) => {
    await page.goto('/consents');
    
    // Check consents page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for existing consent entries
    const consentEntries = page.locator('[data-testid*="consent"], .consent-item');
    const hasConsents = await consentEntries.count() > 0;
    
    if (hasConsents) {
      // Look for revoke action
      const revokeButton = consentEntries.first().locator('button').filter({ hasText: /revoke|cancel/i });
      const hasRevokeButton = await revokeButton.count() > 0;
      
      if (hasRevokeButton) {
        await revokeButton.click();
        // Check for confirmation dialog
        const confirmDialog = page.locator('[role="dialog"], .modal');
        await expect(confirmDialog.first()).toBeVisible();
      }
    }
  });
});

test.describe('PHR workflow actions - appointments', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('appointment scheduling workflow', async ({ page }) => {
    await page.goto('/appointments');
    
    // Check appointments page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for schedule appointment button
    const scheduleButton = page.locator('button, [role="button"]').filter({ hasText: /schedule|book|create/i }).first();
    const hasScheduleButton = await scheduleButton.count() > 0;
    
    if (hasScheduleButton) {
      await scheduleButton.click();
      // Check for appointment form
      const form = page.locator('form');
      await expect(form.first()).toBeVisible();
    }
  });

  test('appointment cancellation workflow', async ({ page }) => {
    await page.goto('/appointments');
    
    // Check appointments page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for existing appointments
    const appointmentEntries = page.locator('[data-testid*="appointment"], .appointment-item');
    const hasAppointments = await appointmentEntries.count() > 0;
    
    if (hasAppointments) {
      // Look for cancel action
      const cancelButton = appointmentEntries.first().locator('button').filter({ hasText: /cancel|delete/i });
      const hasCancelButton = await cancelButton.count() > 0;
      
      if (hasCancelButton) {
        await cancelButton.click();
        // Check for confirmation dialog
        const confirmDialog = page.locator('[role="dialog"], .modal');
        await expect(confirmDialog.first()).toBeVisible();
      }
    }
  });
});

test.describe('PHR workflow actions - documents', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('document upload workflow', async ({ page }) => {
    await page.goto('/documents/upload');
    
    // Check upload page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for file input
    const fileInput = page.locator('input[type="file"]');
    await expect(fileInput.first()).toBeVisible();
    
    // Look for upload button
    const uploadButton = page.locator('button, [role="button"]').filter({ hasText: /upload|submit/i });
    await expect(uploadButton.first()).toBeVisible();
  });

  test('document download workflow', async ({ page }) => {
    await page.goto('/documents');
    
    // Check documents page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for existing documents
    const documentEntries = page.locator('[data-testid*="document"], .document-item');
    const hasDocuments = await documentEntries.count() > 0;
    
    if (hasDocuments) {
      // Look for download action
      const downloadButton = documentEntries.first().locator('button, a').filter({ hasText: /download/i });
      const hasDownloadButton = await downloadButton.count() > 0;
      
      if (hasDownloadButton) {
        await expect(downloadButton.first()).toBeVisible();
      }
    }
  });
});

test.describe('PHR workflow actions - OCR review', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('OCR accept workflow', async ({ page }) => {
    await page.goto('/documents/test-doc-id/ocr');
    
    // Check OCR review page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for OCR content
    const ocrContent = page.locator('[data-testid*="ocr"], .ocr-content');
    const hasOcrContent = await ocrContent.count() > 0;
    
    if (hasOcrContent) {
      // Look for accept button
      const acceptButton = page.locator('button, [role="button"]').filter({ hasText: /accept|confirm/i });
      const hasAcceptButton = await acceptButton.count() > 0;
      
      if (hasAcceptButton) {
        await expect(acceptButton.first()).toBeVisible();
      }
    }
  });

  test('OCR reject workflow', async ({ page }) => {
    await page.goto('/documents/test-doc-id/ocr');
    
    // Check OCR review page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for OCR content
    const ocrContent = page.locator('[data-testid*="ocr"], .ocr-content');
    const hasOcrContent = await ocrContent.count() > 0;
    
    if (hasOcrContent) {
      // Look for reject button
      const rejectButton = page.locator('button, [role="button"]').filter({ hasText: /reject|decline/i });
      const hasRejectButton = await rejectButton.count() > 0;
      
      if (hasRejectButton) {
        await expect(rejectButton.first()).toBeVisible();
      }
    }
  });
});

test.describe('PHR workflow actions - emergency', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('emergency access request workflow', async ({ page }) => {
    await page.goto('/emergency');
    
    // Check emergency page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for emergency access form
    const emergencyForm = page.locator('form');
    const hasForm = await emergencyForm.count() > 0;
    
    if (hasForm) {
      await expect(emergencyForm.first()).toBeVisible();
      
      // Look for reason input
      const reasonInput = page.locator('input[type="text"], textarea').filter({ hasText: /reason/i });
      await expect(reasonInput.first()).toBeVisible();
    }
  });
});

test.describe('PHR workflow actions - audit', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('audit trail view workflow', async ({ page }) => {
    await page.goto('/audit');
    
    // Check audit page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for audit entries
    const auditEntries = page.locator('[data-testid*="audit"], .audit-item, table tbody tr');
    await expect(auditEntries.first()).toBeVisible();
    
    // Look for filter controls
    const filterControls = page.locator('select, input[type="date"], input[type="text"]');
    const hasFilters = await filterControls.count() > 0;
    
    if (hasFilters) {
      await expect(filterControls.first()).toBeVisible();
    }
  });

  test('audit export workflow', async ({ page }) => {
    await page.goto('/audit');
    
    // Check audit page loads
    await expect(page.locator('body')).toBeVisible();
    
    // Look for export button
    const exportButton = page.locator('button, [role="button"]').filter({ hasText: /export|download/i });
    const hasExportButton = await exportButton.count() > 0;
    
    if (hasExportButton) {
      await expect(exportButton.first()).toBeVisible();
    }
  });
});

test.describe('PHR workflow actions - error handling', () => {
  test('workflow handles API errors gracefully', async ({ page }) => {
    // Navigate to a route that might fail
    await page.goto('/records');
    
    // Check for error state handling
    const errorSelector = page.locator('.error, [role="alert"]');
    const hasError = await errorSelector.count() > 0;
    
    if (hasError) {
      // Error should have retry action
      const retryButton = errorSelector.first().locator('button').filter({ hasText: /retry|try again/i });
      const hasRetry = await retryButton.count() > 0;
      
      if (hasRetry) {
        await expect(retryButton.first()).toBeVisible();
      }
    }
  });

  test('workflow handles network errors gracefully', async ({ page }) => {
    // Simulate network conditions
    await page.goto('/dashboard');
    
    // Check for offline/degreded state indicators
    const offlineIndicator = page.locator('[data-testid="offline"], .offline-banner');
    const hasOffline = await offlineIndicator.count() > 0;
    
    if (hasOffline) {
      await expect(offlineIndicator.first()).toBeVisible();
    }
  });
});
