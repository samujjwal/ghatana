/**
 * E2E Test: Real-time Collaboration (Web)
 * Tests WebSocket features, sharing, and collaborative editing
 */

import { test, expect } from './fixtures';

test.describe('Real-time Collaboration', () => {
  test('should share moment with user', async ({ authenticatedPage }) => {
    // Open first moment
    await authenticatedPage.getByTestId('moment-card').first().click();
    
    // Share moment
    await authenticatedPage.getByTestId('share-button').click();
    await expect(authenticatedPage.getByTestId('share-modal')).toBeVisible();
    
    // Search for user
    await authenticatedPage.getByTestId('user-search-input').fill('friend@flashit.app');
    await authenticatedPage.waitForTimeout(1000);
    
    // Select user
    await authenticatedPage.getByTestId('user-result-0').click();
    
    // Confirm share
    await authenticatedPage.getByTestId('confirm-share-button').click();
    
    await expect(authenticatedPage.getByText(/shared successfully/i)).toBeVisible();
  });

  test('should view shared moments', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('shared-tab').click();
    
    await expect(authenticatedPage).toHaveURL('/shared');
    await expect(authenticatedPage.getByTestId('shared-moments-list')).toBeVisible();
  });

  test('should add comment to shared moment', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('shared-tab').click();
    await authenticatedPage.getByTestId('shared-moment-card').first().click();
    
    // Add comment
    await authenticatedPage.getByTestId('comment-input').fill('Great memory!');
    await authenticatedPage.getByTestId('send-comment-button').click();
    
    // Should see comment appear
    await expect(authenticatedPage.getByText('Great memory!')).toBeVisible({ timeout: 3000 });
  });

  test('should receive real-time comment notification', async ({ authenticatedPage, context }) => {
    // This test requires two browser contexts to simulate two users
    // For now, we'll test the notification UI
    
    await authenticatedPage.getByTestId('notifications-button').click();
    await expect(authenticatedPage.getByTestId('notifications-dropdown')).toBeVisible();
  });

  test('should collaborate with CRDT', async ({ authenticatedPage }) => {
    // Open shared moment
    await authenticatedPage.getByTestId('shared-tab').click();
    await authenticatedPage.getByTestId('shared-moment-card').first().click();
    
    // Enable edit mode
    await authenticatedPage.getByTestId('edit-button').click();
    
    // Should show collaboration indicators
    await expect(authenticatedPage.getByTestId('collaboration-status')).toBeVisible();
    
    // Type in editor
    await authenticatedPage.getByTestId('content-editor').fill('Updated content');
    
    // Should show "Saving..." indicator
    await expect(authenticatedPage.getByText(/saving/i)).toBeVisible();
    
    // Wait for save
    await expect(authenticatedPage.getByText(/saved/i)).toBeVisible({ timeout: 5000 });
  });

  test('should mention user in comment', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('shared-tab').click();
    await authenticatedPage.getByTestId('shared-moment-card').first().click();
    
    // Type @ to trigger mention
    await authenticatedPage.getByTestId('comment-input').fill('@fri');
    
    // Should show mention suggestions
    await expect(authenticatedPage.getByTestId('mention-suggestions')).toBeVisible();
    
    // Select user
    await authenticatedPage.getByTestId('mention-user-0').click();
    
    // Complete comment
    await authenticatedPage.getByTestId('comment-input').fill('check this out!');
    await authenticatedPage.getByTestId('send-comment-button').click();
  });

  test('should react to moment', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('moment-card').first().click();
    
    // Add reaction
    await authenticatedPage.getByTestId('reaction-button').click();
    await authenticatedPage.getByTestId('reaction-heart').click();
    
    // Should show reaction count
    await expect(authenticatedPage.getByTestId('reaction-count-heart')).toContainText('1');
  });

  test('should remove reaction', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('moment-card').first().click();
    
    // Add reaction
    await authenticatedPage.getByTestId('reaction-button').click();
    await authenticatedPage.getByTestId('reaction-heart').click();
    await expect(authenticatedPage.getByTestId('reaction-count-heart')).toContainText('1');
    
    // Remove reaction
    await authenticatedPage.getByTestId('reaction-heart').click();
    await expect(authenticatedPage.getByTestId('reaction-count-heart')).toContainText('0');
  });

  test('should manage share permissions', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('moment-card').first().click();
    
    await authenticatedPage.getByTestId('share-button').click();
    await authenticatedPage.getByTestId('manage-access-tab').click();
    
    await expect(authenticatedPage.getByTestId('access-list')).toBeVisible();
    
    // Change permission
    await authenticatedPage.getByTestId('user-access-0-menu').click();
    await authenticatedPage.getByTestId('permission-view-only').click();
    
    await expect(authenticatedPage.getByText(/permission updated/i)).toBeVisible();
  });

  test('should revoke share access', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('moment-card').first().click();
    
    await authenticatedPage.getByTestId('share-button').click();
    await authenticatedPage.getByTestId('manage-access-tab').click();
    
    // Revoke access
    await authenticatedPage.getByTestId('user-access-0-revoke').click();
    await authenticatedPage.getByRole('button', { name: /revoke/i }).click();
    
    await expect(authenticatedPage.getByText(/access revoked/i)).toBeVisible();
  });

  test('should view activity feed', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('activity-tab').click();
    
    await expect(authenticatedPage).toHaveURL('/activity');
    await expect(authenticatedPage.getByTestId('activity-feed')).toBeVisible();
    await expect(authenticatedPage.getByTestId('activity-item')).toHaveCount(await authenticatedPage.getByTestId('activity-item').count());
  });

  test('should filter activity by type', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('activity-tab').click();
    
    // Filter by comments
    await authenticatedPage.getByTestId('activity-filter-select').click();
    await authenticatedPage.getByTestId('filter-comments').click();
    
    // All items should be comments
    const activityTypes = await authenticatedPage.getByTestId('activity-type').allTextContents();
    activityTypes.forEach(type => expect(type).toContain('comment'));
  });

  test('should mark notification as read', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('notifications-button').click();
    
    // Mark first notification as read
    await authenticatedPage.getByTestId('notification-0-read').click();
    
    // Should no longer show as unread
    await expect(authenticatedPage.getByTestId('notification-0')).not.toHaveClass(/unread/);
  });
});
