/**
 * E2E Test Suite - Bootstrapping Collaboration
 *
 * @description Tests real-time collaboration features including
 * inviting collaborators, cursor presence, and comments.
 *
 * @doc.type test
 * @doc.purpose E2E collaboration validation
 * @doc.layer e2e
 * @doc.phase bootstrapping
 */

import { test, expect, Page, BrowserContext } from '@playwright/test';
import {
  StartProjectPage,
  BootstrapSessionPage,
  BootstrapCollaboratePage,
} from './pages/bootstrapping.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Bootstrapping Collaboration', () => {
  let context1: BrowserContext;
  let context2: BrowserContext;
  let page1: Page;
  let page2: Page;

  test.beforeAll(async ({ browser }) => {
    // Create two separate browser contexts for two users
    context1 = await browser.newContext();
    context2 = await browser.newContext();
  });

  test.afterAll(async () => {
    await context1.close();
    await context2.close();
  });

  test.beforeEach(async () => {
    page1 = await context1.newPage();
    page2 = await context2.newPage();

    // Login user 1
    await page1.goto('/login');
    await page1.getByLabel(/email/i).fill(testUsers.standard.email);
    await page1.getByLabel(/password/i).fill(testUsers.standard.password);
    await page1.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page1).toHaveURL(/dashboard|projects/);

    // Login user 2 (admin)
    await page2.goto('/login');
    await page2.getByLabel(/email/i).fill(testUsers.admin.email);
    await page2.getByLabel(/password/i).fill(testUsers.admin.password);
    await page2.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page2).toHaveURL(/dashboard|projects/);
  });

  test.afterEach(async () => {
    await page1.close();
    await page2.close();
  });

  // ==========================================================================
  // Invite Collaborator Tests
  // ==========================================================================

  test('should invite collaborator to session', async () => {
    // User 1 creates a session
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);
    const collaboratePage1 = new BootstrapCollaboratePage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Collaborative project test');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page1.url();
    const sessionId = sessionUrl.split('/').pop() || '';

    await sessionPage1.waitForAIResponse();

    // Navigate to collaborate page
    await collaboratePage1.goto(sessionId);

    // Invite user 2
    await collaboratePage1.inviteCollaborator(testUsers.admin.email);

    // Should show success message
    await expect(page1.getByText(/invited|invitation sent/i)).toBeVisible({ timeout: 5000 });
  });

  test('should show collaborator in session after accepting invite', async () => {
    // User 1 creates and shares
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);
    const collaboratePage1 = new BootstrapCollaboratePage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Shared canvas project');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page1.url();
    const sessionId = sessionUrl.split('/').pop() || '';

    await sessionPage1.waitForAIResponse();
    await sessionPage1.share();

    // Get the share URL or invite user directly
    const shareUrl = sessionUrl + '/collaborate';

    // User 2 joins via URL
    await page2.goto(shareUrl);

    // User 2 should see the canvas
    const collaboratePage2 = new BootstrapCollaboratePage(page2);
    await expect(collaboratePage2.collaboratorsList).toBeVisible();

    // User 1 should see user 2 in collaborators list
    await collaboratePage1.goto(sessionId);
    const collaboratorCount = await collaboratePage1.getCollaboratorCount();
    expect(collaboratorCount).toBeGreaterThanOrEqual(2);
  });

  // ==========================================================================
  // Real-time Presence Tests
  // ==========================================================================

  test('should show collaborator cursor in real-time', async () => {
    // Setup: Both users in same session
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Cursor tracking project');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page1.url();

    await sessionPage1.waitForAIResponse();
    await sessionPage1.share();

    // User 2 joins
    await page2.goto(sessionUrl);
    const sessionPage2 = new BootstrapSessionPage(page2);

    // Wait for both to be connected
    await expect(sessionPage2.canvasPanel).toBeVisible();

    // User 2 moves cursor on canvas
    const canvasBox = await sessionPage2.canvasPanel.boundingBox();
    if (canvasBox) {
      await page2.mouse.move(canvasBox.x + 100, canvasBox.y + 100);
    }

    // User 1 should see user 2's cursor
    const remoteCursor = page1.locator('[data-testid="remote-cursor"]');
    await expect(remoteCursor).toBeVisible({ timeout: 5000 });
  });

  test('should show collaborator avatars in presence list', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Avatar presence test');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page1.url();

    await sessionPage1.waitForAIResponse();
    await sessionPage1.share();

    // User 2 joins
    await page2.goto(sessionUrl);

    // Check for presence indicators
    const presenceIndicator = page1.getByTestId('collaborators-presence');
    await expect(presenceIndicator).toBeVisible({ timeout: 10000 });

    // Should show multiple avatars
    const avatars = presenceIndicator.locator('[data-testid="collaborator-avatar"]');
    await expect(avatars).toHaveCount(2, { timeout: 5000 });
  });

  // ==========================================================================
  // Comment Thread Tests
  // ==========================================================================

  test('should allow adding comments to canvas nodes', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);
    const collaboratePage1 = new BootstrapCollaboratePage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Comment test project');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page1.url().split('/').pop() || '';

    await sessionPage1.waitForAIResponse();
    await sessionPage1.sendMessage('Create some features to comment on');
    await sessionPage1.waitForAIResponse();

    // Go to collaborate page
    await collaboratePage1.goto(sessionId);

    // Open comments panel
    await collaboratePage1.openComments();

    // Add a comment
    await collaboratePage1.addComment('This feature needs more detail');

    // Comment should appear
    await expect(page1.getByText('This feature needs more detail')).toBeVisible();
  });

  test('should show comments from other collaborators in real-time', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);
    const collaboratePage1 = new BootstrapCollaboratePage(page1);
    const collaboratePage2 = new BootstrapCollaboratePage(page2);

    // User 1 creates session
    await startPage1.goto();
    await startPage1.startWithIdea('Real-time comment sync');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page1.url();
    const sessionId = sessionUrl.split('/').pop() || '';

    await sessionPage1.waitForAIResponse();
    await sessionPage1.share();

    // User 2 joins
    await page2.goto(sessionUrl + '/collaborate');

    // Both open comments
    await collaboratePage1.goto(sessionId);
    await collaboratePage1.openComments();
    await collaboratePage2.openComments();

    // User 2 adds comment
    await collaboratePage2.addComment('Comment from user 2');

    // User 1 should see it in real-time
    await expect(page1.getByText('Comment from user 2')).toBeVisible({ timeout: 10000 });
  });

  test('should allow replying to comments', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);
    const collaboratePage1 = new BootstrapCollaboratePage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Reply test project');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page1.url().split('/').pop() || '';

    await sessionPage1.waitForAIResponse();

    await collaboratePage1.goto(sessionId);
    await collaboratePage1.openComments();

    // Add initial comment
    await collaboratePage1.addComment('Initial comment');

    // Reply to comment
    const replyButton = page1.getByRole('button', { name: /reply/i }).first();
    await replyButton.click();

    const replyInput = page1.getByPlaceholder(/reply|respond/i);
    await replyInput.fill('This is a reply');
    await page1.getByRole('button', { name: /post|submit/i }).last().click();

    // Reply should appear
    await expect(page1.getByText('This is a reply')).toBeVisible();
  });

  test('should allow resolving comments', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);
    const collaboratePage1 = new BootstrapCollaboratePage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Resolve comment test');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionId = page1.url().split('/').pop() || '';

    await sessionPage1.waitForAIResponse();

    await collaboratePage1.goto(sessionId);
    await collaboratePage1.openComments();

    // Add comment
    await collaboratePage1.addComment('Issue to resolve');

    // Resolve comment
    const resolveButton = page1.getByRole('button', { name: /resolve/i }).first();
    await resolveButton.click();

    // Comment should be marked as resolved
    const resolvedIndicator = page1.locator('[data-testid="comment-resolved"]');
    await expect(resolvedIndicator).toBeVisible();
  });

  // ==========================================================================
  // Canvas Sync Tests
  // ==========================================================================

  test('should sync canvas changes between collaborators', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);
    const sessionPage2 = new BootstrapSessionPage(page2);

    // User 1 creates session
    await startPage1.goto();
    await startPage1.startWithIdea('Canvas sync test');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page1.url();

    await sessionPage1.waitForAIResponse();
    await sessionPage1.share();

    // User 2 joins
    await page2.goto(sessionUrl);
    await expect(sessionPage2.canvasPanel).toBeVisible();

    // Get initial node count for user 2
    const initialNodeCount = await sessionPage2.getNodeCount();

    // User 1 adds content via conversation
    await sessionPage1.sendMessage('Add authentication, user profiles, and settings');
    await sessionPage1.waitForAIResponse();

    // User 2 should see new nodes appear
    await expect(async () => {
      const newNodeCount = await sessionPage2.getNodeCount();
      expect(newNodeCount).toBeGreaterThan(initialNodeCount);
    }).toPass({ timeout: 15000 });
  });

  // ==========================================================================
  // Permission Tests
  // ==========================================================================

  test('should respect collaborator permissions (view only vs edit)', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);
    const collaboratePage1 = new BootstrapCollaboratePage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Permission test project');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page1.url();
    const sessionId = sessionUrl.split('/').pop() || '';

    await sessionPage1.waitForAIResponse();

    // Invite with view-only permission
    await collaboratePage1.goto(sessionId);
    await collaboratePage1.inviteButton.click();
    await collaboratePage1.inviteInput.fill(testUsers.admin.email);
    
    // Select view-only permission
    const permissionSelect = page1.getByLabel(/permission|role/i);
    if (await permissionSelect.isVisible()) {
      await permissionSelect.selectOption('viewer');
    }
    
    await collaboratePage1.sendInviteButton.click();

    // User 2 joins as viewer
    await page2.goto(sessionUrl);
    const sessionPage2 = new BootstrapSessionPage(page2);

    // Check if editing is disabled for viewer
    const messageInput = sessionPage2.messageInput;
    const isDisabled = await messageInput.isDisabled();
    
    // Either the input is disabled or there's a "view only" indicator
    if (!isDisabled) {
      const viewOnlyBadge = page2.getByText(/view only|viewer/i);
      await expect(viewOnlyBadge).toBeVisible();
    }
  });

  // ==========================================================================
  // Disconnection Handling Tests
  // ==========================================================================

  test('should handle collaborator disconnection gracefully', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Disconnect handling test');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page1.url();

    await sessionPage1.waitForAIResponse();
    await sessionPage1.share();

    // User 2 joins
    await page2.goto(sessionUrl);
    const presenceIndicator = page1.getByTestId('collaborators-presence');
    await expect(presenceIndicator.locator('[data-testid="collaborator-avatar"]')).toHaveCount(2, {
      timeout: 10000,
    });

    // User 2 disconnects
    await page2.close();

    // User 1 should see user 2 disappear from presence
    await expect(presenceIndicator.locator('[data-testid="collaborator-avatar"]')).toHaveCount(1, {
      timeout: 15000,
    });
  });

  test('should attempt reconnection after network issues', async () => {
    const startPage1 = new StartProjectPage(page1);
    const sessionPage1 = new BootstrapSessionPage(page1);

    await startPage1.goto();
    await startPage1.startWithIdea('Reconnection test');

    await expect(page1).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage1.waitForAIResponse();

    // Simulate network disconnection
    await page1.context().setOffline(true);

    // Should show disconnection indicator
    const connectionStatus = page1.getByTestId('connection-status');
    await expect(connectionStatus).toContainText(/disconnected|reconnecting/i, { timeout: 5000 });

    // Reconnect
    await page1.context().setOffline(false);

    // Should reconnect
    await expect(connectionStatus).toContainText(/connected/i, { timeout: 15000 });
  });
});
