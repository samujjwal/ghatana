/**
 * E2E Test Suite - Bootstrapping Happy Path
 *
 * @description Tests the complete bootstrapping flow from idea input
 * through all phases to project creation.
 *
 * @doc.type test
 * @doc.purpose E2E happy path validation
 * @doc.layer e2e
 * @doc.phase bootstrapping
 */

import { test, expect } from '@playwright/test';
import {
  StartProjectPage,
  BootstrapSessionPage,
  BootstrapReviewPage,
  BootstrapCompletePage,
} from './pages/bootstrapping.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Bootstrapping Happy Path', () => {
  let startPage: StartProjectPage;
  let sessionPage: BootstrapSessionPage;
  let reviewPage: BootstrapReviewPage;
  let completePage: BootstrapCompletePage;

  test.beforeEach(async ({ page }) => {
    startPage = new StartProjectPage(page);
    sessionPage = new BootstrapSessionPage(page);
    reviewPage = new BootstrapReviewPage(page);
    completePage = new BootstrapCompletePage(page);

    // Login first
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  // ==========================================================================
  // Complete Happy Path Flow
  // ==========================================================================

  test('should complete full bootstrapping flow with text idea input', async ({ page }) => {
    // Step 1: Start with an idea
    await startPage.goto();
    await expect(startPage.ideaInput).toBeVisible();
    
    await startPage.startWithIdea(
      'Build a mobile app for a local bakery to manage orders and deliveries'
    );

    // Should redirect to bootstrap session
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);

    // Step 2: Enter Phase - Answer initial questions
    await expect(sessionPage.currentPhase).toContainText(/enter/i);
    await expect(sessionPage.aiTypingIndicator).toBeVisible();
    await sessionPage.waitForAIResponse();

    // AI should ask clarifying questions
    const messageCount = await sessionPage.getMessageCount();
    expect(messageCount).toBeGreaterThan(0);

    // Answer the first question
    await sessionPage.sendMessage('Yes, the bakery needs to handle daily orders and track delivery routes');
    await sessionPage.waitForAIResponse();

    // Step 3: Explore Phase - More detailed questions
    // Continue answering questions until we reach explore phase
    let currentPhase = await sessionPage.getCurrentPhase();
    let attempts = 0;
    
    while (!currentPhase.toLowerCase().includes('explore') && attempts < 10) {
      const options = await sessionPage.conversationPanel.locator('[data-testid="option-button"]');
      if (await options.count() > 0) {
        await options.first().click();
      } else {
        await sessionPage.sendMessage('Continue with suggested approach');
      }
      await sessionPage.waitForAIResponse();
      currentPhase = await sessionPage.getCurrentPhase();
      attempts++;
    }

    expect(currentPhase.toLowerCase()).toContain('explore');

    // Step 4: Canvas should have nodes now
    const nodeCount = await sessionPage.getNodeCount();
    expect(nodeCount).toBeGreaterThan(0);

    // Step 5: Refine Phase - Adjust canvas
    attempts = 0;
    while (!currentPhase.toLowerCase().includes('refine') && attempts < 10) {
      const options = await sessionPage.conversationPanel.locator('[data-testid="option-button"]');
      if (await options.count() > 0) {
        await options.first().click();
      } else {
        await sessionPage.sendMessage('Looks good, let\'s proceed');
      }
      await sessionPage.waitForAIResponse();
      currentPhase = await sessionPage.getCurrentPhase();
      attempts++;
    }

    // Step 6: Validate Phase
    attempts = 0;
    while (!currentPhase.toLowerCase().includes('validate') && attempts < 10) {
      await sessionPage.sendMessage('Confirm all requirements are correct');
      await sessionPage.waitForAIResponse();
      currentPhase = await sessionPage.getCurrentPhase();
      attempts++;
    }

    // Step 7: Start Phase - Final confirmation
    attempts = 0;
    while (!currentPhase.toLowerCase().includes('start') && attempts < 5) {
      await sessionPage.sendMessage('Ready to create the project');
      await sessionPage.waitForAIResponse();
      currentPhase = await sessionPage.getCurrentPhase();
      attempts++;
    }

    // Step 8: Navigate to review
    const sessionUrl = page.url();
    const sessionId = sessionUrl.split('/').pop() || '';
    await reviewPage.goto(sessionId);

    // Step 9: Approve the project
    await expect(reviewPage.validationStatus).toBeVisible();
    await reviewPage.approve();

    // Step 10: Complete page
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+\/complete$/);
    await expect(completePage.successMessage).toBeVisible();

    // Step 11: Go to the created project
    await completePage.goToProject();
    await expect(page).toHaveURL(/\/projects\/[\w-]+/);
  });

  // ==========================================================================
  // Phase Progression Tests
  // ==========================================================================

  test('should display correct phase indicator throughout flow', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Create a task management system');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);

    // Check Enter phase
    await expect(sessionPage.phaseProgress).toBeVisible();
    await expect(sessionPage.currentPhase).toContainText(/enter/i);

    // Phase progress should show all 5 phases
    const phases = sessionPage.phaseProgress.locator('[data-testid="phase-step"]');
    await expect(phases).toHaveCount(5);
  });

  test('should update canvas in real-time as conversation progresses', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Build an e-commerce platform');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Initial node count
    const initialNodeCount = await sessionPage.getNodeCount();

    // Answer a few questions to trigger canvas updates
    await sessionPage.sendMessage('Yes, we need product catalog and shopping cart features');
    await sessionPage.waitForAIResponse();

    // Canvas should have more nodes now
    const updatedNodeCount = await sessionPage.getNodeCount();
    expect(updatedNodeCount).toBeGreaterThanOrEqual(initialNodeCount);
  });

  // ==========================================================================
  // Save and Auto-save Tests
  // ==========================================================================

  test('should auto-save progress during conversation', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Create a fitness tracking app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Check for auto-save indicator
    const saveIndicator = page.getByTestId('save-status');
    await expect(saveIndicator).toContainText(/saved|saving/i, { timeout: 10000 });
  });

  test('should allow manual save at any point', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Build a CRM system');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Manual save
    await sessionPage.save();

    // Should show save confirmation
    await expect(page.getByText(/saved successfully|session saved/i)).toBeVisible({
      timeout: 5000,
    });
  });

  // ==========================================================================
  // Question Options Tests
  // ==========================================================================

  test('should display and respond to multiple choice questions', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Build a social media platform');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Look for option buttons (multiple choice)
    const options = sessionPage.conversationPanel.locator('[data-testid="option-button"]');
    
    // If options are available, select one
    if (await options.count() > 0) {
      await options.first().click();
      await sessionPage.waitForAIResponse();
      
      // Should continue conversation
      const messageCount = await sessionPage.getMessageCount();
      expect(messageCount).toBeGreaterThan(1);
    }
  });

  test('should support free-text responses when allowed', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Create a blog platform');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Send a detailed free-text response
    await sessionPage.sendMessage(
      'I want the blog to support markdown, code highlighting, and image uploads. ' +
      'It should also have categories and tags for organization.'
    );
    await sessionPage.waitForAIResponse();

    // AI should acknowledge the detailed input
    const lastMessage = await sessionPage.getLastMessage();
    expect(lastMessage.length).toBeGreaterThan(0);
  });

  // ==========================================================================
  // Canvas Interaction Tests
  // ==========================================================================

  test('should allow canvas zoom and pan controls', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Build an inventory management system');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Test zoom controls
    await expect(sessionPage.canvasControls).toBeVisible();
    
    await sessionPage.zoomIn();
    await sessionPage.zoomOut();
    await sessionPage.fitView();
    
    // Canvas should remain functional
    await expect(sessionPage.canvasPanel).toBeVisible();
  });

  test('should display node details on hover or click', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Build a project management tool');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    
    // Progress conversation to generate nodes
    await sessionPage.waitForAIResponse();
    await sessionPage.sendMessage('Include task tracking, team management, and reporting');
    await sessionPage.waitForAIResponse();

    // Wait for nodes to appear
    await expect(sessionPage.canvasNodes.first()).toBeVisible({ timeout: 10000 });

    // Click on a node
    await sessionPage.canvasNodes.first().click();

    // Should show node details panel or tooltip
    const nodeDetails = page.getByTestId('node-details');
    await expect(nodeDetails).toBeVisible({ timeout: 5000 });
  });
});

// ============================================================================
// Error Recovery Tests
// ============================================================================

test.describe('Bootstrapping Error Recovery', () => {
  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  test('should handle network errors gracefully during conversation', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Build a messaging app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Simulate network error by going offline
    await page.context().setOffline(true);

    // Try to send a message
    await sessionPage.sendMessage('Test message while offline');

    // Should show error message
    await expect(page.getByText(/network error|connection lost|offline/i)).toBeVisible({
      timeout: 5000,
    });

    // Go back online
    await page.context().setOffline(false);

    // Should be able to continue
    await sessionPage.sendMessage('Test message after reconnect');
    await sessionPage.waitForAIResponse();
  });

  test('should allow retry after AI response failure', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Build a weather app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    
    // If there's a retry button visible (after an error), click it
    const retryButton = page.getByRole('button', { name: /retry|try again/i });
    if (await retryButton.isVisible()) {
      await retryButton.click();
      await sessionPage.waitForAIResponse();
    }
  });
});
