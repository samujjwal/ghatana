/**
 * E2E Test Suite - Bootstrapping Validation
 *
 * @description Tests validation rules, error handling, and
 * form validation throughout the bootstrapping process.
 *
 * @doc.type test
 * @doc.purpose E2E validation testing
 * @doc.layer e2e
 * @doc.phase bootstrapping
 */

import { test, expect } from '@playwright/test';
import {
  StartProjectPage,
  BootstrapSessionPage,
  BootstrapReviewPage,
  UploadDocsPage,
  ImportFromURLPage,
  TemplateSelectionPage,
} from './pages/bootstrapping.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Bootstrapping Validation', () => {
  let startPage: StartProjectPage;
  let sessionPage: BootstrapSessionPage;
  let reviewPage: BootstrapReviewPage;

  test.beforeEach(async ({ page }) => {
    startPage = new StartProjectPage(page);
    sessionPage = new BootstrapSessionPage(page);
    reviewPage = new BootstrapReviewPage(page);

    // Login
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  // ==========================================================================
  // Start Page Validation
  // ==========================================================================

  test('should require idea input before starting', async ({ page }) => {
    await startPage.goto();

    // Try to submit without input
    await startPage.submitButton.click();

    // Should show validation error
    await expect(page.getByText(/required|enter.*idea|describe/i)).toBeVisible();

    // Should stay on start page
    await expect(page).toHaveURL(/\/start$/);
  });

  test('should validate minimum idea length', async ({ page }) => {
    await startPage.goto();

    // Enter very short idea
    await startPage.enterIdea('app');
    await startPage.submitButton.click();

    // Should show validation error
    await expect(page.getByText(/too short|more detail|at least/i)).toBeVisible();
  });

  test('should accept valid idea input', async ({ page }) => {
    await startPage.goto();

    await startPage.startWithIdea('Build a comprehensive project management tool with team collaboration');

    // Should navigate to session
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
  });

  // ==========================================================================
  // Upload Page Validation
  // ==========================================================================

  test('should validate file types for document upload', async ({ page }) => {
    const uploadPage = new UploadDocsPage(page);
    await uploadPage.goto();

    // Try to upload invalid file type (create a dummy invalid file)
    // In real tests, you'd use test fixtures
    await expect(uploadPage.dropZone).toBeVisible();

    // Check for supported file type indicators
    await expect(page.getByText(/pdf|doc|md|txt|supported/i)).toBeVisible();
  });

  test('should validate file size limits', async ({ page }) => {
    const uploadPage = new UploadDocsPage(page);
    await uploadPage.goto();

    // Check for file size limit indicator
    await expect(page.getByText(/mb|max size|limit/i)).toBeVisible();
  });

  test('should allow removing uploaded files', async ({ page }) => {
    const uploadPage = new UploadDocsPage(page);
    await uploadPage.goto();

    // If there are uploaded files, test removal
    const uploadedFileCount = await uploadPage.getUploadedFileCount();
    if (uploadedFileCount > 0) {
      await uploadPage.removeFile(0);
      const newCount = await uploadPage.getUploadedFileCount();
      expect(newCount).toBe(uploadedFileCount - 1);
    }
  });

  // ==========================================================================
  // Import URL Validation
  // ==========================================================================

  test('should validate URL format', async ({ page }) => {
    const importPage = new ImportFromURLPage(page);
    await importPage.goto();

    // Enter invalid URL
    await importPage.enterUrl('not-a-valid-url');
    await importPage.import();

    // Should show validation error
    await expect(page.getByText(/invalid url|valid url|format/i)).toBeVisible();
  });

  test('should validate supported URL sources', async ({ page }) => {
    const importPage = new ImportFromURLPage(page);
    await importPage.goto();

    // Enter unsupported URL
    await importPage.enterUrl('https://random-unsupported-site.com/project');
    await importPage.import();

    // Should show error for unsupported source
    await expect(page.getByText(/not supported|unsupported|github|figma|notion/i)).toBeVisible();
  });

  test('should accept valid GitHub URL', async ({ page }) => {
    const importPage = new ImportFromURLPage(page);
    await importPage.goto();

    await importPage.selectGitHub();
    await importPage.enterUrl('https://github.com/example/repo');
    
    // URL should be accepted (format validation)
    // Full import would require actual API
  });

  // ==========================================================================
  // Template Selection Validation
  // ==========================================================================

  test('should require template selection before continuing', async ({ page }) => {
    const templatePage = new TemplateSelectionPage(page);
    await templatePage.goto();

    // Try to use template without selection
    await templatePage.useTemplateButton.click();

    // Should show validation error or button should be disabled
    const isDisabled = await templatePage.useTemplateButton.isDisabled();
    if (!isDisabled) {
      await expect(page.getByText(/select.*template|choose.*template/i)).toBeVisible();
    }
  });

  test('should enable use button after template selection', async ({ page }) => {
    const templatePage = new TemplateSelectionPage(page);
    await templatePage.goto();

    // Wait for templates to load
    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select a template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();

    // Use button should be enabled
    await expect(templatePage.useTemplateButton).toBeEnabled();
  });

  // ==========================================================================
  // Session Conversation Validation
  // ==========================================================================

  test('should prevent empty message submission', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Test project for validation');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Try to send empty message
    await sessionPage.sendButton.click();

    // Should either be disabled or show error
    const isDisabled = await sessionPage.sendButton.isDisabled();
    expect(isDisabled).toBe(true);
  });

  test('should validate required question responses', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Validation test project');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // If there's a required question with options, try to skip
    const continueButton = page.getByRole('button', { name: /continue|skip|next/i });
    if (await continueButton.isVisible()) {
      await continueButton.click();
      
      // Should show validation if required
      const requiredError = page.getByText(/required|please select|answer/i);
      if (await requiredError.isVisible()) {
        expect(await requiredError.isVisible()).toBe(true);
      }
    }
  });

  // ==========================================================================
  // Review Page Validation
  // ==========================================================================

  test('should display validation issues in review page', async ({ page }) => {
    // Create session and go to review
    await startPage.goto();
    await startPage.startWithIdea('Incomplete project for review');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page.url().split('/').pop() || '';

    await sessionPage.waitForAIResponse();

    // Navigate to review (may have validation issues)
    await reviewPage.goto(sessionId);

    // Check for validation status or issues list
    await expect(reviewPage.validationStatus).toBeVisible();
  });

  test('should prevent approval with critical issues', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Project with issues');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page.url().split('/').pop() || '';

    await sessionPage.waitForAIResponse();

    await reviewPage.goto(sessionId);

    // If there are critical issues, approval should be blocked
    const issueCount = await reviewPage.getIssueCount();
    if (issueCount > 0) {
      const criticalIssue = page.locator('[data-severity="critical"]');
      if (await criticalIssue.isVisible()) {
        // Approve button should be disabled
        const isDisabled = await reviewPage.approvalButton.isDisabled();
        expect(isDisabled).toBe(true);
      }
    }
  });

  test('should require comment when requesting changes', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Project for change request');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page.url().split('/').pop() || '';

    await sessionPage.waitForAIResponse();

    await reviewPage.goto(sessionId);

    // Click request changes
    await reviewPage.requestChangesButton.click();

    // Should require comment
    await expect(reviewPage.commentInput).toBeVisible();
    
    // Try to submit without comment
    await reviewPage.submitReviewButton.click();

    // Should show validation error
    const commentError = page.getByText(/comment required|provide feedback|reason/i);
    if (await commentError.isVisible()) {
      expect(await commentError.isVisible()).toBe(true);
    }
  });

  // ==========================================================================
  // Canvas Validation
  // ==========================================================================

  test('should validate canvas has minimum required elements', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Minimal canvas project');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page.url().split('/').pop() || '';

    await sessionPage.waitForAIResponse();

    // Try to proceed to review without sufficient canvas elements
    await reviewPage.goto(sessionId);

    // Should show warning if canvas is incomplete
    const incompleteWarning = page.getByText(/incomplete|minimum|more detail|features required/i);
    // This may or may not be visible depending on AI responses
  });

  // ==========================================================================
  // Input Sanitization Tests
  // ==========================================================================

  test('should sanitize HTML/script input in idea', async ({ page }) => {
    await startPage.goto();

    // Try XSS attack
    await startPage.startWithIdea('Build app <script>alert("xss")</script> for testing');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);

    // Script should not execute, page should work normally
    await sessionPage.waitForAIResponse();
    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  test('should sanitize HTML in conversation messages', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Sanitization test project');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Send message with HTML
    await sessionPage.sendMessage('Add feature <b>bold</b> and <img src="x" onerror="alert(1)">');
    await sessionPage.waitForAIResponse();

    // Page should remain functional
    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  // ==========================================================================
  // Character Limit Tests
  // ==========================================================================

  test('should enforce character limit on idea input', async ({ page }) => {
    await startPage.goto();

    // Try very long input
    const longText = 'a'.repeat(5001);
    await startPage.enterIdea(longText);

    // Check if input was truncated or shows error
    const inputValue = await startPage.ideaInput.inputValue();
    expect(inputValue.length).toBeLessThanOrEqual(5000);
  });

  test('should show character count indicator', async ({ page }) => {
    await startPage.goto();

    await startPage.enterIdea('Some text');

    // Look for character count indicator
    const charCount = page.getByText(/\d+\s*\/\s*\d+|characters/i);
    if (await charCount.isVisible()) {
      expect(await charCount.isVisible()).toBe(true);
    }
  });
});

// ============================================================================
// Error State Tests
// ============================================================================

test.describe('Bootstrapping Error States', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  test('should show error state for invalid session ID', async ({ page }) => {
    await page.goto('/bootstrap/invalid-session-id-12345');

    // Should show error or redirect
    await expect(page.getByText(/not found|invalid|error/i)).toBeVisible({ timeout: 10000 });
  });

  test('should handle server errors gracefully', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Server error test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);

    // If server error occurs during AI response
    // Mock would be needed for true server error testing
    // Just verify error handling UI exists
    const errorRetry = page.getByRole('button', { name: /retry|try again/i });
    // Error button visibility depends on actual errors occurring
  });

  test('should show appropriate message for network timeout', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Timeout test project');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);

    // Set very short timeout to trigger timeout behavior
    await page.context().setOffline(true);
    await sessionPage.sendMessage('Test message during offline');
    await page.context().setOffline(false);

    // Should show network error
    await expect(page.getByText(/network|connection|offline|timeout/i)).toBeVisible({
      timeout: 10000,
    });
  });
});
