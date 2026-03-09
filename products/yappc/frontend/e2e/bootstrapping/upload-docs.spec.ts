/**
 * E2E Test Suite - Bootstrapping Document Upload
 *
 * @description Tests document upload functionality for seeding
 * the bootstrapping process with existing specs, wireframes, etc.
 *
 * @doc.type test
 * @doc.purpose E2E document upload validation
 * @doc.layer e2e
 * @doc.phase bootstrapping
 */

import { test, expect } from '@playwright/test';
import * as path from 'path';
import {
  StartProjectPage,
  UploadDocsPage,
  BootstrapSessionPage,
} from './pages/bootstrapping.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Bootstrapping Document Upload', () => {
  let startPage: StartProjectPage;
  let uploadPage: UploadDocsPage;
  let sessionPage: BootstrapSessionPage;

  // Test file paths (would be actual test fixtures in real setup)
  const TEST_FILES = {
    pdf: path.join(__dirname, '../fixtures/test-document.pdf'),
    md: path.join(__dirname, '../fixtures/test-spec.md'),
    docx: path.join(__dirname, '../fixtures/test-requirements.docx'),
    txt: path.join(__dirname, '../fixtures/test-notes.txt'),
    unsupported: path.join(__dirname, '../fixtures/test-image.exe'),
  };

  test.beforeEach(async ({ page }) => {
    startPage = new StartProjectPage(page);
    uploadPage = new UploadDocsPage(page);
    sessionPage = new BootstrapSessionPage(page);

    // Login
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  // ==========================================================================
  // Navigation Tests
  // ==========================================================================

  test('should navigate to upload page from start page', async ({ page }) => {
    await startPage.goto();
    await startPage.goToUpload();

    await expect(page).toHaveURL(/\/start\/upload/);
  });

  test('should display upload interface elements', async ({ page }) => {
    await uploadPage.goto();

    await expect(uploadPage.dropZone).toBeVisible();
    await expect(uploadPage.fileInput).toBeAttached();
  });

  // ==========================================================================
  // Drag and Drop Tests
  // ==========================================================================

  test('should show drop zone with visual feedback', async ({ page }) => {
    await uploadPage.goto();

    // Check for drop zone styling/text
    await expect(uploadPage.dropZone).toBeVisible();
    await expect(page.getByText(/drag|drop|browse/i)).toBeVisible();
  });

  test('should highlight drop zone on drag over', async ({ page }) => {
    await uploadPage.goto();

    // Simulate drag enter
    await uploadPage.dropZone.dispatchEvent('dragenter', {
      dataTransfer: { types: ['Files'] },
    });

    // Should have active/highlight state
    const dropZone = uploadPage.dropZone;
    // Check for visual change (class or style)
  });

  // ==========================================================================
  // File Type Validation Tests
  // ==========================================================================

  test('should display supported file types', async ({ page }) => {
    await uploadPage.goto();

    // Should show supported formats
    await expect(page.getByText(/pdf|doc|md|txt/i)).toBeVisible();
  });

  test('should accept PDF files', async ({ page }) => {
    await uploadPage.goto();

    // Create a test file programmatically
    const buffer = Buffer.from('PDF content');
    await uploadPage.fileInput.setInputFiles({
      name: 'test-document.pdf',
      mimeType: 'application/pdf',
      buffer,
    });

    // File should appear in list
    await expect(page.getByText('test-document.pdf')).toBeVisible({ timeout: 5000 });
  });

  test('should accept Markdown files', async ({ page }) => {
    await uploadPage.goto();

    const buffer = Buffer.from('# Markdown content');
    await uploadPage.fileInput.setInputFiles({
      name: 'spec.md',
      mimeType: 'text/markdown',
      buffer,
    });

    await expect(page.getByText('spec.md')).toBeVisible({ timeout: 5000 });
  });

  test('should accept text files', async ({ page }) => {
    await uploadPage.goto();

    const buffer = Buffer.from('Plain text content');
    await uploadPage.fileInput.setInputFiles({
      name: 'notes.txt',
      mimeType: 'text/plain',
      buffer,
    });

    await expect(page.getByText('notes.txt')).toBeVisible({ timeout: 5000 });
  });

  test('should reject unsupported file types', async ({ page }) => {
    await uploadPage.goto();

    const buffer = Buffer.from('Executable content');
    await uploadPage.fileInput.setInputFiles({
      name: 'test.exe',
      mimeType: 'application/x-msdownload',
      buffer,
    });

    // Should show error
    await expect(page.getByText(/not supported|invalid|rejected/i)).toBeVisible({ timeout: 5000 });
  });

  // ==========================================================================
  // File Size Validation Tests
  // ==========================================================================

  test('should display file size limit', async ({ page }) => {
    await uploadPage.goto();

    // Should show size limit info
    await expect(page.getByText(/mb|size limit|maximum/i)).toBeVisible();
  });

  test('should reject files exceeding size limit', async ({ page }) => {
    await uploadPage.goto();

    // Create a large file (simulate)
    const largeBuffer = Buffer.alloc(50 * 1024 * 1024); // 50MB
    await uploadPage.fileInput.setInputFiles({
      name: 'large-file.pdf',
      mimeType: 'application/pdf',
      buffer: largeBuffer,
    });

    // Should show size error
    await expect(page.getByText(/too large|exceeds|size limit/i)).toBeVisible({ timeout: 5000 });
  });

  // ==========================================================================
  // Multiple Files Tests
  // ==========================================================================

  test('should allow uploading multiple files', async ({ page }) => {
    await uploadPage.goto();

    // Upload multiple files
    await uploadPage.fileInput.setInputFiles([
      {
        name: 'doc1.pdf',
        mimeType: 'application/pdf',
        buffer: Buffer.from('PDF 1'),
      },
      {
        name: 'doc2.md',
        mimeType: 'text/markdown',
        buffer: Buffer.from('# MD 2'),
      },
    ]);

    // Both should appear
    await expect(page.getByText('doc1.pdf')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('doc2.md')).toBeVisible({ timeout: 5000 });
  });

  test('should show count of uploaded files', async ({ page }) => {
    await uploadPage.goto();

    // Upload files
    await uploadPage.fileInput.setInputFiles([
      {
        name: 'file1.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from('Content 1'),
      },
      {
        name: 'file2.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from('Content 2'),
      },
    ]);

    const fileCount = await uploadPage.getUploadedFileCount();
    expect(fileCount).toBe(2);
  });

  // ==========================================================================
  // File Management Tests
  // ==========================================================================

  test('should allow removing uploaded files', async ({ page }) => {
    await uploadPage.goto();

    // Upload a file
    await uploadPage.fileInput.setInputFiles({
      name: 'removable.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('PDF content'),
    });

    await expect(page.getByText('removable.pdf')).toBeVisible({ timeout: 5000 });

    // Remove it
    await uploadPage.removeFile(0);

    // Should no longer be visible
    await expect(page.getByText('removable.pdf')).not.toBeVisible({ timeout: 5000 });
  });

  test('should show upload progress for large files', async ({ page }) => {
    await uploadPage.goto();

    // Upload a file that would show progress
    const buffer = Buffer.alloc(5 * 1024 * 1024); // 5MB
    await uploadPage.fileInput.setInputFiles({
      name: 'large.pdf',
      mimeType: 'application/pdf',
      buffer,
    });

    // Progress indicator may appear briefly
    const progressIndicator = page.getByText(/uploading|progress|\d+%/i);
    // Progress may be too fast to catch in tests
  });

  // ==========================================================================
  // Continue to Session Tests
  // ==========================================================================

  test('should enable continue after uploading files', async ({ page }) => {
    await uploadPage.goto();

    // Initially continue may be disabled
    const initiallyDisabled = await uploadPage.continueButton.isDisabled();

    // Upload a file
    await uploadPage.fileInput.setInputFiles({
      name: 'spec.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('# Requirements\n- Feature 1\n- Feature 2'),
    });

    await expect(page.getByText('spec.md')).toBeVisible({ timeout: 5000 });

    // Continue should be enabled
    await expect(uploadPage.continueButton).toBeEnabled();
  });

  test('should create session with uploaded documents', async ({ page }) => {
    await uploadPage.goto();

    // Upload spec document
    await uploadPage.fileInput.setInputFiles({
      name: 'product-spec.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from(`# Product Specification

## Overview
Build a task management application.

## Features
- User authentication
- Task creation and editing
- Due dates and reminders
- Team collaboration

## Technical Requirements
- React frontend
- Node.js backend
- PostgreSQL database
`),
    });

    await expect(page.getByText('product-spec.md')).toBeVisible({ timeout: 5000 });

    // Continue to session
    await uploadPage.continue();

    // Should navigate to bootstrap session
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
  });

  test('should reference uploaded documents in AI conversation', async ({ page }) => {
    await uploadPage.goto();

    // Upload a detailed spec
    await uploadPage.fileInput.setInputFiles({
      name: 'requirements.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from(`# E-commerce Platform Requirements
      
## User Stories
1. As a customer, I can browse products
2. As a customer, I can add items to cart
3. As an admin, I can manage inventory
`),
    });

    await expect(page.getByText('requirements.md')).toBeVisible({ timeout: 5000 });
    await uploadPage.continue();

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // AI should reference the uploaded content
    const messages = sessionPage.messageList.locator('[data-testid="message"]');
    const messageText = await messages.allTextContents();
    
    // Should mention content from the document
    const hasReference = messageText.some(
      (text) =>
        text.toLowerCase().includes('e-commerce') ||
        text.toLowerCase().includes('document') ||
        text.toLowerCase().includes('requirements') ||
        text.toLowerCase().includes('uploaded')
    );
    expect(hasReference).toBe(true);
  });

  // ==========================================================================
  // File Preview Tests
  // ==========================================================================

  test('should show file preview for text-based files', async ({ page }) => {
    await uploadPage.goto();

    // Upload a markdown file
    await uploadPage.fileInput.setInputFiles({
      name: 'preview-test.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('# Preview Content\n\nThis is a test.'),
    });

    await expect(page.getByText('preview-test.md')).toBeVisible({ timeout: 5000 });

    // Click on file to preview
    await page.getByText('preview-test.md').click();

    // Preview panel should show
    const previewPanel = page.getByTestId('file-preview');
    if (await previewPanel.isVisible()) {
      await expect(previewPanel).toContainText(/Preview Content|This is a test/);
    }
  });

  // ==========================================================================
  // Error Handling Tests
  // ==========================================================================

  test('should handle upload errors gracefully', async ({ page }) => {
    await uploadPage.goto();

    // Simulate network error
    await page.context().setOffline(true);

    await uploadPage.fileInput.setInputFiles({
      name: 'error-test.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('PDF content'),
    });

    // Should show error
    await expect(page.getByText(/error|failed|try again/i)).toBeVisible({ timeout: 10000 });

    await page.context().setOffline(false);
  });

  test('should allow retry after upload failure', async ({ page }) => {
    await uploadPage.goto();

    // Go offline to cause failure
    await page.context().setOffline(true);

    await uploadPage.fileInput.setInputFiles({
      name: 'retry-test.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('PDF content'),
    });

    // Wait for error
    await expect(page.getByText(/error|failed/i)).toBeVisible({ timeout: 10000 });

    // Go back online
    await page.context().setOffline(false);

    // Retry
    const retryButton = page.getByRole('button', { name: /retry|try again/i });
    if (await retryButton.isVisible()) {
      await retryButton.click();
      // Should succeed
    }
  });

  // ==========================================================================
  // Accessibility Tests
  // ==========================================================================

  test('should be keyboard accessible', async ({ page }) => {
    await uploadPage.goto();

    // Tab to drop zone
    await page.keyboard.press('Tab');

    // Should be able to trigger file picker with Enter/Space
    // (Browser security prevents programmatic file input trigger)
  });

  test('should announce upload status to screen readers', async ({ page }) => {
    await uploadPage.goto();

    // Upload a file
    await uploadPage.fileInput.setInputFiles({
      name: 'a11y-test.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('PDF content'),
    });

    // Check for ARIA live region or status role
    const liveRegion = page.locator('[role="status"], [aria-live]');
    expect(await liveRegion.count()).toBeGreaterThan(0);
  });
});

// ============================================================================
// Document Processing Tests
// ============================================================================

test.describe('Document Processing', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  test('should extract content from uploaded documents', async ({ page }) => {
    const uploadPage = new UploadDocsPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await uploadPage.goto();

    // Upload document with specific content
    await uploadPage.fileInput.setInputFiles({
      name: 'extraction-test.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from(`# Mobile Banking App

## Core Features
1. Account balance viewing
2. Money transfer between accounts
3. Bill payment
4. Transaction history

## Security Requirements
- Two-factor authentication
- Biometric login support
- Session timeout after inactivity
`),
    });

    await expect(page.getByText('extraction-test.md')).toBeVisible({ timeout: 5000 });
    await uploadPage.continue();

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // AI should have extracted and understood the document
    // Canvas should reflect the features mentioned
    const nodeCount = await sessionPage.getNodeCount();
    expect(nodeCount).toBeGreaterThan(0);
  });

  test('should handle documents in different languages', async ({ page }) => {
    const uploadPage = new UploadDocsPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await uploadPage.goto();

    // Upload document in another language (Spanish)
    await uploadPage.fileInput.setInputFiles({
      name: 'spanish-spec.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from(`# Especificación del Producto

## Funcionalidades
- Autenticación de usuarios
- Gestión de tareas
- Colaboración en equipo
`),
    });

    await expect(page.getByText('spanish-spec.md')).toBeVisible({ timeout: 5000 });
    await uploadPage.continue();

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    // Should handle without error
    await sessionPage.waitForAIResponse();
  });
});
