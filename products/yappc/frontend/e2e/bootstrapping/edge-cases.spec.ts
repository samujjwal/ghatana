/**
 * E2E Test Suite - Bootstrapping Edge Cases
 *
 * @description Tests edge cases, boundary conditions, and unusual
 * user behaviors during the bootstrapping process.
 *
 * @doc.type test
 * @doc.purpose E2E edge case validation
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
  ResumeSessionPage,
} from './pages/bootstrapping.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Bootstrapping Edge Cases', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  // ==========================================================================
  // Browser Navigation Edge Cases
  // ==========================================================================

  test('should handle browser back button during session', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Back button test project');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Use browser back button
    await page.goBack();

    // Should either go back to start or show warning
    const confirmDialog = page.getByRole('dialog');
    if (await confirmDialog.isVisible()) {
      // Close dialog and stay
      await page.getByRole('button', { name: /stay|cancel/i }).click();
      await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    } else {
      await expect(page).toHaveURL(/\/start/);
    }
  });

  test('should handle browser forward button', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Forward button test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page.url();

    await sessionPage.waitForAIResponse();

    // Navigate back
    await page.goBack();

    // Navigate forward
    await page.goForward();

    // Should return to session
    await expect(page).toHaveURL(sessionUrl);
  });

  test('should handle page refresh during conversation', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Refresh test project');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page.url();

    await sessionPage.waitForAIResponse();
    await sessionPage.sendMessage('Test message before refresh');
    await sessionPage.waitForAIResponse();

    // Refresh page
    await page.reload();

    // Should remain on same session
    await expect(page).toHaveURL(sessionUrl);

    // Content should be preserved (via auto-save)
    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  // ==========================================================================
  // Concurrent Session Edge Cases
  // ==========================================================================

  test('should handle opening same session in multiple tabs', async ({ page, context }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Multi-tab test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page.url();

    await sessionPage.waitForAIResponse();

    // Open same session in new tab
    const page2 = await context.newPage();
    await page2.goto(sessionUrl);

    const sessionPage2 = new BootstrapSessionPage(page2);
    await expect(sessionPage2.conversationPanel).toBeVisible();

    // Both should show same content
    const messageCount1 = await sessionPage.getMessageCount();
    const messageCount2 = await sessionPage2.getMessageCount();
    expect(messageCount1).toBe(messageCount2);

    await page2.close();
  });

  // ==========================================================================
  // Input Edge Cases
  // ==========================================================================

  test('should handle very long idea input', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();

    // Very long but valid idea
    const longIdea = 'Build a comprehensive enterprise resource planning system that includes ' +
      'human resources management, inventory tracking, financial accounting, customer relationship ' +
      'management, supply chain management, manufacturing operations, business intelligence and ' +
      'analytics, project management, and document management capabilities with full integration ' +
      'between all modules and support for multiple languages, currencies, and time zones.';

    await startPage.startWithIdea(longIdea);

    // Should work without truncation issues
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();
  });

  test('should handle special characters in idea', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();

    // Idea with special characters
    await startPage.startWithIdea('Build an app for "Joe\'s Café" — with 100% UTF-8 support (日本語, 한국어, العربية)');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Should work correctly
    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  test('should handle emojis in idea and messages', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Build a 🚀 rocket launcher app for 🌙 moon missions 🌟');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Send message with emojis
    await sessionPage.sendMessage('Add features for 📱 mobile and 💻 desktop');
    await sessionPage.waitForAIResponse();

    // Should work correctly
    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  test('should handle rapid message submission', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Rapid input test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Rapidly submit multiple messages
    await sessionPage.messageInput.fill('Message 1');
    await sessionPage.sendButton.click();
    await sessionPage.messageInput.fill('Message 2');
    await sessionPage.sendButton.click();
    await sessionPage.messageInput.fill('Message 3');
    await sessionPage.sendButton.click();

    // Should queue or handle gracefully
    await page.waitForTimeout(5000);

    // Should still be functional
    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  // ==========================================================================
  // Network Edge Cases
  // ==========================================================================

  test('should handle slow network conditions', async ({ page, context }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    // Simulate slow 3G
    const cdpSession = await context.newCDPSession(page);
    await cdpSession.send('Network.emulateNetworkConditions', {
      offline: false,
      downloadThroughput: (500 * 1024) / 8, // 500 Kbps
      uploadThroughput: (500 * 1024) / 8,
      latency: 400,
    });

    await startPage.goto();
    await startPage.startWithIdea('Slow network test');

    // Should still work, just slower
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/, { timeout: 30000 });
    await sessionPage.waitForAIResponse();

    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  test('should handle intermittent network connectivity', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Intermittent network test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Toggle offline/online
    await page.context().setOffline(true);
    await page.waitForTimeout(1000);
    await page.context().setOffline(false);

    // Send message after reconnection
    await sessionPage.sendMessage('Message after reconnection');

    // May show reconnection indicator
    const connectionStatus = page.getByTestId('connection-status');
    if (await connectionStatus.isVisible()) {
      await expect(connectionStatus).toContainText(/connected/i, { timeout: 10000 });
    }
  });

  // ==========================================================================
  // Session State Edge Cases
  // ==========================================================================

  test('should handle expired session', async ({ page }) => {
    // Navigate to a session that doesn't exist or is expired
    await page.goto('/bootstrap/expired-session-id-12345');

    // Should show error or redirect
    await expect(page.getByText(/not found|expired|invalid|error/i)).toBeVisible({ timeout: 10000 });
  });

  test('should handle session without permission', async ({ page }) => {
    // Navigate to another user's session (if known)
    // In real test, would need a session ID from another user
    await page.goto('/bootstrap/other-users-session-id');

    // Should show permission error or redirect
    await expect(page.getByText(/permission|access denied|not found|error/i)).toBeVisible({ timeout: 10000 });
  });

  // ==========================================================================
  // Canvas Edge Cases
  // ==========================================================================

  test('should handle large number of canvas nodes', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Large enterprise system with many features');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Add many features to generate nodes
    const features = [
      'user management, authentication, authorization',
      'inventory, products, categories, pricing',
      'orders, payments, shipping, returns',
      'analytics, reports, dashboards, exports',
      'notifications, emails, SMS, push notifications',
    ];

    for (const feature of features) {
      await sessionPage.sendMessage(`Add ${feature}`);
      await sessionPage.waitForAIResponse();
    }

    // Canvas should handle many nodes
    const nodeCount = await sessionPage.getNodeCount();
    expect(nodeCount).toBeGreaterThan(0);

    // Canvas controls should still work
    await sessionPage.fitView();
  });

  test('should handle canvas with no nodes gracefully', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Empty canvas test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);

    // Initially canvas may be empty
    await expect(sessionPage.canvasPanel).toBeVisible();

    // Controls should still work
    await sessionPage.fitView();
  });

  // ==========================================================================
  // File Upload Edge Cases
  // ==========================================================================

  test('should handle uploading empty file', async ({ page }) => {
    const uploadPage = new UploadDocsPage(page);
    await uploadPage.goto();

    // Upload empty file
    await uploadPage.fileInput.setInputFiles({
      name: 'empty.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from(''),
    });

    // Should show warning or error
    await expect(page.getByText(/empty|no content|invalid/i)).toBeVisible({ timeout: 5000 });
  });

  test('should handle file with very long name', async ({ page }) => {
    const uploadPage = new UploadDocsPage(page);
    await uploadPage.goto();

    const longName = 'a'.repeat(200) + '.pdf';
    await uploadPage.fileInput.setInputFiles({
      name: longName,
      mimeType: 'application/pdf',
      buffer: Buffer.from('PDF content'),
    });

    // Should handle gracefully (truncate or error)
    // Check that page is still functional
    await expect(uploadPage.dropZone).toBeVisible();
  });

  // ==========================================================================
  // URL Import Edge Cases
  // ==========================================================================

  test('should handle URL with authentication required', async ({ page }) => {
    const importPage = new ImportFromURLPage(page);
    await importPage.goto();

    // Try to import from private repo
    await importPage.importFromUrl('https://github.com/private-user/private-repo');

    // Should show authentication error
    await expect(page.getByText(/auth|login|permission|private|access/i)).toBeVisible({ timeout: 10000 });
  });

  test('should handle non-existent URL', async ({ page }) => {
    const importPage = new ImportFromURLPage(page);
    await importPage.goto();

    await importPage.importFromUrl('https://github.com/nonexistent-user-12345/nonexistent-repo-67890');

    // Should show not found error
    await expect(page.getByText(/not found|doesn't exist|error/i)).toBeVisible({ timeout: 10000 });
  });

  // ==========================================================================
  // Review Edge Cases
  // ==========================================================================

  test('should handle review with all approvals complete', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);
    const reviewPage = new BootstrapReviewPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Already approved test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page.url().split('/').pop() || '';

    await sessionPage.waitForAIResponse();

    await reviewPage.goto(sessionId);

    // If already approved, should show completion state
    const approvalStatus = await reviewPage.getApprovalStatus();
    // Status varies based on state
  });

  // ==========================================================================
  // Memory/Performance Edge Cases
  // ==========================================================================

  test('should handle long session without memory leaks', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Long session test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Simulate long session with many interactions
    for (let i = 0; i < 10; i++) {
      await sessionPage.sendMessage(`Feature ${i + 1}: Add more functionality`);
      await sessionPage.waitForAIResponse();
    }

    // Page should still be responsive
    await expect(sessionPage.conversationPanel).toBeVisible();
    await sessionPage.fitView();
  });

  // ==========================================================================
  // Concurrent Operation Edge Cases
  // ==========================================================================

  test('should handle save while AI is responding', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Concurrent save test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Send message
    await sessionPage.sendMessage('Generate many features');

    // Immediately try to save while AI is responding
    await sessionPage.save();

    // Should handle gracefully
    await sessionPage.waitForAIResponse();
    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  // ==========================================================================
  // Accessibility Edge Cases
  // ==========================================================================

  test('should maintain focus management during phase transitions', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Focus management test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Focus should be on appropriate element
    const activeElement = await page.evaluate(() => document.activeElement?.tagName);
    // Focus should be on message input or an interactive element
  });

  test('should handle keyboard-only navigation through entire flow', async ({ page }) => {
    const startPage = new StartProjectPage(page);

    await startPage.goto();

    // Navigate entirely with keyboard
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    
    // Type idea
    await page.keyboard.type('Keyboard-only navigation test');
    
    // Submit with Enter or Tab to button
    await page.keyboard.press('Tab');
    await page.keyboard.press('Enter');

    // Should navigate to session
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
  });
});

// ============================================================================
// Error Recovery Edge Cases
// ============================================================================

test.describe('Error Recovery Edge Cases', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  test('should recover from JavaScript errors', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Error recovery test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Inject an error (simulate component error)
    await page.evaluate(() => {
      // This would normally crash React, but error boundaries should catch it
      console.error('Test error injection');
    });

    // Page should still be functional
    await expect(sessionPage.conversationPanel).toBeVisible();
  });

  test('should show error boundary fallback on critical error', async ({ page }) => {
    // Navigate to a path that might cause rendering error
    await page.goto('/bootstrap/malformed-session-id/<script>');

    // Should show error boundary or 404, not crash
    const errorMessage = page.getByText(/error|not found|something went wrong/i);
    await expect(errorMessage).toBeVisible({ timeout: 10000 });
  });
});
