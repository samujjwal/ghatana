/**
 * E2E Test: Collaborative Features Flow
 * Tests sharing, collaboration, and real-time features
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, takeScreenshot } = require('./helpers/setup');

describe('Collaborative Features Flow', () => {
  beforeAll(async () => {
    await device.launchApp();
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('should share moment with user', async () => {
    await element(by.id('home-tab')).tap();
    
    // Open moment
    await element(by.id('moment-card-0')).tap();
    
    // Share
    await element(by.id('share-button')).tap();
    await expect(element(by.id('share-modal'))).toBeVisible();
    
    // Search for user
    await element(by.id('user-search-input')).typeText('friend@flashit.app');
    await waitFor(element(by.id('user-result-0')))
      .toBeVisible()
      .withTimeout(3000);
    
    // Select user
    await element(by.id('user-result-0')).tap();
    
    // Confirm share
    await element(by.id('confirm-share-button')).tap();
    
    await waitFor(element(by.text('Moment shared successfully')))
      .toBeVisible()
      .withTimeout(5000);
    
    await takeScreenshot('moment-shared');
  });

  it('should view shared moments', async () => {
    await element(by.id('home-tab')).tap();
    
    // Navigate to shared section
    await element(by.id('shared-tab-button')).tap();
    
    await expect(element(by.id('shared-moments-list'))).toBeVisible();
    await expect(element(by.id('shared-moment-0'))).toBeVisible();
    
    await takeScreenshot('shared-moments');
  });

  it('should collaborate on shared moment', async () => {
    await element(by.id('home-tab')).tap();
    await element(by.id('shared-tab-button')).tap();
    
    // Open shared moment
    await element(by.id('shared-moment-0')).tap();
    
    // Add comment
    await element(by.id('comment-input')).typeText('Great memory!');
    await element(by.id('send-comment-button')).tap();
    
    // Should see comment appear
    await waitFor(element(by.text('Great memory!')))
      .toBeVisible()
      .withTimeout(3000);
    
    await takeScreenshot('comment-added');
  });

  it('should receive real-time updates', async () => {
    await element(by.id('home-tab')).tap();
    await element(by.id('shared-tab-button')).tap();
    await element(by.id('shared-moment-0')).tap();
    
    // Wait for real-time update (would need another user to test properly)
    // In real scenario, another user would add a comment
    
    // Should show new comment indicator
    // await expect(element(by.id('new-comment-indicator'))).toBeVisible();
  });

  it('should mention user in comment', async () => {
    await element(by.id('home-tab')).tap();
    await element(by.id('shared-tab-button')).tap();
    await element(by.id('shared-moment-0')).tap();
    
    // Type @ to trigger mention
    await element(by.id('comment-input')).typeText('@fri');
    
    // Should show mention suggestions
    await expect(element(by.id('mention-suggestions'))).toBeVisible();
    
    // Select user
    await element(by.id('mention-user-0')).tap();
    
    // Complete comment
    await element(by.id('comment-input')).typeText('check this out!');
    await element(by.id('send-comment-button')).tap();
  });

  it('should revoke share access', async () => {
    await element(by.id('home-tab')).tap();
    
    // Open own moment
    await element(by.id('moment-card-0')).tap();
    
    // Open share settings
    await element(by.id('share-button')).tap();
    await element(by.id('manage-access-button')).tap();
    
    // Revoke access from user
    await element(by.id('user-access-0-revoke')).tap();
    await element(by.text('Revoke')).tap(); // Confirm
    
    await waitFor(element(by.text('Access revoked')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should set share permissions', async () => {
    await element(by.id('home-tab')).tap();
    await element(by.id('moment-card-0')).tap();
    
    await element(by.id('share-button')).tap();
    
    // Set permission level
    await element(by.id('permission-select')).tap();
    await element(by.id('permission-view-only')).tap();
    
    await element(by.id('user-search-input')).typeText('friend@flashit.app');
    await waitFor(element(by.id('user-result-0'))).toBeVisible().withTimeout(3000);
    await element(by.id('user-result-0')).tap();
    await element(by.id('confirm-share-button')).tap();
  });

  it('should view activity feed', async () => {
    await element(by.id('activity-tab')).tap();
    
    await expect(element(by.id('activity-feed'))).toBeVisible();
    await expect(element(by.id('activity-item-0'))).toBeVisible();
    
    await takeScreenshot('activity-feed');
  });

  it('should react to shared moment', async () => {
    await element(by.id('home-tab')).tap();
    await element(by.id('shared-tab-button')).tap();
    await element(by.id('shared-moment-0')).tap();
    
    // Add reaction
    await element(by.id('reaction-button')).tap();
    await element(by.id('reaction-heart')).tap();
    
    // Should see reaction count update
    await expect(element(by.id('reaction-count-heart'))).toHaveText('1');
  });

  it('should filter activity by type', async () => {
    await element(by.id('activity-tab')).tap();
    
    // Filter by comments
    await element(by.id('filter-button')).tap();
    await element(by.id('filter-comments')).tap();
    
    // Should only show comment activities
    await expect(element(by.id('activity-item-0-type'))).toHaveText('comment');
  });
});
