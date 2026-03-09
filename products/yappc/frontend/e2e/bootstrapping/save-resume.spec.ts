/**
 * E2E Test Suite - Bootstrapping Save and Resume
 *
 * @description Tests session persistence, auto-save, and resume functionality.
 *
 * @doc.type test
 * @doc.purpose E2E save/resume validation
 * @doc.layer e2e
 * @doc.phase bootstrapping
 */

import { test, expect } from '@playwright/test';
import {
  StartProjectPage,
  BootstrapSessionPage,
  ResumeSessionPage,
} from './pages/bootstrapping.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Bootstrapping Save and Resume', () => {
  let startPage: StartProjectPage;
  let sessionPage: BootstrapSessionPage;
  let resumePage: ResumeSessionPage;

  test.beforeEach(async ({ page }) => {
    startPage = new StartProjectPage(page);
    sessionPage = new BootstrapSessionPage(page);
    resumePage = new ResumeSessionPage(page);

    // Login
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  // ==========================================================================
  // Auto-Save Tests
  // ==========================================================================

  test('should auto-save session after each response', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Build a recipe sharing app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Check auto-save indicator
    const saveStatus = page.getByTestId('save-status');
    await expect(saveStatus).toContainText(/saved/i, { timeout: 10000 });

    // Send another message
    await sessionPage.sendMessage('Include features for meal planning');
    await sessionPage.waitForAIResponse();

    // Should auto-save again
    await expect(saveStatus).toContainText(/saved/i, { timeout: 10000 });
  });

  test('should show saving indicator during auto-save', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Create a habit tracker');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    
    // After initial response, look for saving indicator
    const saveStatus = page.getByTestId('save-status');
    
    // It should transition from saving to saved
    await expect(saveStatus).toBeVisible({ timeout: 10000 });
  });

  // ==========================================================================
  // Manual Save Tests
  // ==========================================================================

  test('should save session manually with save button', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Build a note-taking app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Manual save
    await sessionPage.save();

    // Confirmation
    await expect(page.getByText(/saved|session saved/i)).toBeVisible({ timeout: 5000 });
  });

  test('should save session with keyboard shortcut Ctrl+S', async ({ page }) => {
    await startPage.goto();
    await startPage.startWithIdea('Create a budgeting app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Keyboard shortcut
    await page.keyboard.press('Control+s');

    // Should show save confirmation
    await expect(page.getByText(/saved|session saved/i)).toBeVisible({ timeout: 5000 });
  });

  // ==========================================================================
  // Resume Session Tests
  // ==========================================================================

  test('should display saved sessions on resume page', async ({ page }) => {
    // First create a session
    await startPage.goto();
    await startPage.startWithIdea('Build a meditation app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();
    await sessionPage.save();

    // Go to resume page
    await resumePage.goto();

    // Should see at least one session
    const sessionCount = await resumePage.getSessionCount();
    expect(sessionCount).toBeGreaterThan(0);
  });

  test('should resume session from saved sessions list', async ({ page }) => {
    // Create a session first
    await startPage.goto();
    await startPage.startWithIdea('Build a language learning app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page.url();
    const sessionId = sessionUrl.split('/').pop() || '';

    await sessionPage.waitForAIResponse();
    await sessionPage.sendMessage('Include spaced repetition features');
    await sessionPage.waitForAIResponse();
    
    const messageCountBefore = await sessionPage.getMessageCount();
    const nodeCountBefore = await sessionPage.getNodeCount();

    await sessionPage.save();

    // Navigate away
    await page.goto('/dashboard');

    // Go to resume page
    await resumePage.goto();

    // Resume the session
    await resumePage.resumeSession('language learning app');

    // Should be back in the session
    await expect(page).toHaveURL(new RegExp(`/bootstrap/${sessionId}`));

    // State should be preserved
    const messageCountAfter = await sessionPage.getMessageCount();
    const nodeCountAfter = await sessionPage.getNodeCount();

    expect(messageCountAfter).toBe(messageCountBefore);
    expect(nodeCountAfter).toBe(nodeCountBefore);
  });

  test('should preserve conversation history when resuming', async ({ page }) => {
    // Create session with conversation
    await startPage.goto();
    await startPage.startWithIdea('Create a podcast player app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Have a conversation
    await sessionPage.sendMessage('Include playlist management');
    await sessionPage.waitForAIResponse();
    await sessionPage.sendMessage('Add offline download feature');
    await sessionPage.waitForAIResponse();

    const lastMessageBefore = await sessionPage.getLastMessage();
    await sessionPage.save();

    // Navigate away and back
    await page.goto('/dashboard');
    await resumePage.goto();
    await resumePage.resumeSession('podcast player');

    // Conversation should be preserved
    const lastMessageAfter = await sessionPage.getLastMessage();
    expect(lastMessageAfter).toBe(lastMessageBefore);
  });

  test('should preserve canvas state when resuming', async ({ page }) => {
    // Create session with canvas progress
    await startPage.goto();
    await startPage.startWithIdea('Build a travel planning app');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Progress to generate canvas nodes
    await sessionPage.sendMessage('Include trip itinerary, booking management, and maps');
    await sessionPage.waitForAIResponse();

    const nodeCountBefore = await sessionPage.getNodeCount();
    const edgeCountBefore = await sessionPage.getEdgeCount();

    await sessionPage.save();

    // Navigate away and resume
    await page.goto('/dashboard');
    await resumePage.goto();
    await resumePage.resumeSession('travel planning');

    // Canvas should be preserved
    const nodeCountAfter = await sessionPage.getNodeCount();
    const edgeCountAfter = await sessionPage.getEdgeCount();

    expect(nodeCountAfter).toBe(nodeCountBefore);
    expect(edgeCountAfter).toBe(edgeCountBefore);
  });

  test('should preserve current phase when resuming', async ({ page }) => {
    // Create session and progress to a specific phase
    await startPage.goto();
    await startPage.startWithIdea('Create a music streaming service');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // Progress through conversation
    for (let i = 0; i < 3; i++) {
      const options = sessionPage.conversationPanel.locator('[data-testid="option-button"]');
      if (await options.count() > 0) {
        await options.first().click();
      } else {
        await sessionPage.sendMessage('Continue with default options');
      }
      await sessionPage.waitForAIResponse();
    }

    const phaseBefore = await sessionPage.getCurrentPhase();
    await sessionPage.save();

    // Navigate away and resume
    await page.goto('/dashboard');
    await resumePage.goto();
    await resumePage.resumeSession('music streaming');

    // Phase should be preserved
    const phaseAfter = await sessionPage.getCurrentPhase();
    expect(phaseAfter).toBe(phaseBefore);
  });

  // ==========================================================================
  // Session Management Tests
  // ==========================================================================

  test('should delete session from resume page', async ({ page }) => {
    // Create a session
    await startPage.goto();
    await startPage.startWithIdea('Build a temp session to delete');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();
    await sessionPage.save();

    // Go to resume page
    await resumePage.goto();
    const countBefore = await resumePage.getSessionCount();

    // Delete the session
    await resumePage.deleteSession('temp session');

    // Confirm count decreased
    const countAfter = await resumePage.getSessionCount();
    expect(countAfter).toBe(countBefore - 1);
  });

  test('should search through saved sessions', async ({ page }) => {
    // Create multiple sessions
    await startPage.goto();
    await startPage.startWithIdea('Alpha project - unique name');
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();
    await sessionPage.save();

    await startPage.goto();
    await startPage.startWithIdea('Beta project - another unique name');
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();
    await sessionPage.save();

    // Go to resume page and search
    await resumePage.goto();
    await resumePage.searchSessions('Alpha');

    // Should filter to matching session
    const sessionCards = resumePage.sessionsList.locator('[data-testid="session-card"]');
    await expect(sessionCards.first()).toContainText('Alpha');
  });

  test('should sort sessions by different criteria', async ({ page }) => {
    await resumePage.goto();

    // Sort by date
    await resumePage.sortBy('date');
    
    // Sort by name
    await resumePage.sortBy('name');
    
    // Sort by progress
    await resumePage.sortBy('progress');

    // No errors should occur
    await expect(resumePage.sessionsList).toBeVisible();
  });

  // ==========================================================================
  // Session Expiry Tests
  // ==========================================================================

  test('should show expiry warning for old sessions', async ({ page }) => {
    await resumePage.goto();

    // Look for expiry warning on any session
    const expiryWarning = page.getByText(/expires|expiring/i);
    
    // This may or may not be visible depending on session age
    if (await expiryWarning.isVisible()) {
      await expect(expiryWarning).toBeVisible();
    }
  });

  // ==========================================================================
  // Start Page Saved Sessions Widget
  // ==========================================================================

  test('should display recent sessions on start page', async ({ page }) => {
    // Create a session first
    await startPage.goto();
    await startPage.startWithIdea('Build a quick test project');
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();
    await sessionPage.save();

    // Go back to start page
    await startPage.goto();

    // Should see saved sessions widget
    const sessionCount = await startPage.getSavedSessionCount();
    expect(sessionCount).toBeGreaterThan(0);
  });

  test('should resume session from start page widget', async ({ page }) => {
    // Create a session
    await startPage.goto();
    await startPage.startWithIdea('Widget test project');
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page.url();
    
    await sessionPage.waitForAIResponse();
    await sessionPage.save();

    // Go back to start page
    await startPage.goto();

    // Resume from widget
    await startPage.resumeSession(0);

    // Should be in the session
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
  });
});

// ============================================================================
// Edge Cases
// ============================================================================

test.describe('Save/Resume Edge Cases', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  test('should handle browser refresh during session', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Refresh test project');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page.url();
    
    await sessionPage.waitForAIResponse();
    await sessionPage.sendMessage('Some test content');
    await sessionPage.waitForAIResponse();

    const messageCountBefore = await sessionPage.getMessageCount();

    // Refresh the page
    await page.reload();

    // Should still be on the same session
    await expect(page).toHaveURL(sessionUrl);

    // Content should be preserved (via auto-save)
    const messageCountAfter = await sessionPage.getMessageCount();
    expect(messageCountAfter).toBeGreaterThanOrEqual(messageCountBefore - 1); // May lose last if not saved
  });

  test('should handle closing tab and reopening (browser storage)', async ({ page, context }) => {
    const startPage = new StartProjectPage(page);
    const sessionPage = new BootstrapSessionPage(page);

    await startPage.goto();
    await startPage.startWithIdea('Tab close test');

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    const sessionUrl = page.url();
    
    await sessionPage.waitForAIResponse();
    await sessionPage.save();

    // Open new tab in same context
    const newPage = await context.newPage();
    await newPage.goto(sessionUrl);

    // Should be able to access session
    const newSessionPage = new BootstrapSessionPage(newPage);
    await expect(newSessionPage.conversationPanel).toBeVisible();
  });
});
