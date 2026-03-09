/**
 * E2E Test: Timeline & Search Flow
 * Tests browsing moments, search, and filtering functionality
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, scrollToElement, takeScreenshot } = require('./helpers/setup');

describe('Timeline & Search Flow', () => {
  beforeAll(async () => {
    await device.launchApp();
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('should display timeline with moments', async () => {
    await element(by.id('home-tab')).tap();
    
    await expect(element(by.id('timeline-screen'))).toBeVisible();
    await expect(element(by.id('moments-list'))).toBeVisible();
    
    await takeScreenshot('timeline-home');
  });

  it('should pull to refresh timeline', async () => {
    await element(by.id('home-tab')).tap();
    
    // Pull to refresh
    await element(by.id('moments-list')).swipe('down', 'fast', 0.9, 0.1);
    
    // Should show loading indicator
    await expect(element(by.id('refresh-indicator'))).toBeVisible();
    
    // Wait for refresh to complete
    await waitFor(element(by.id('refresh-indicator')))
      .not.toBeVisible()
      .withTimeout(5000);
  });

  it('should open moment detail', async () => {
    await element(by.id('home-tab')).tap();
    
    // Tap on first moment
    await element(by.id('moment-card-0')).tap();
    
    // Should show detail screen
    await expect(element(by.id('moment-detail-screen'))).toBeVisible();
    await expect(element(by.id('moment-title'))).toBeVisible();
    await expect(element(by.id('moment-content'))).toBeVisible();
    
    await takeScreenshot('moment-detail');
  });

  it('should play audio moment from timeline', async () => {
    await element(by.id('home-tab')).tap();
    
    // Find audio moment (assuming first moment is audio)
    await element(by.id('moment-card-0')).tap();
    
    // Play audio
    await element(by.id('play-audio-button')).tap();
    await expect(element(by.id('audio-player'))).toBeVisible();
    await expect(element(by.id('playback-progress'))).toBeVisible();
    
    await takeScreenshot('audio-playing-timeline');
  });

  it('should play video moment from timeline', async () => {
    await element(by.id('home-tab')).tap();
    
    // Scroll to find video moment
    await scrollToElement('moments-list', by.id('video-moment-card'));
    
    // Tap video thumbnail
    await element(by.id('video-moment-card')).tap();
    
    // Should show video player
    await expect(element(by.id('video-player-fullscreen'))).toBeVisible();
    
    await takeScreenshot('video-playing-timeline');
  });

  it('should search moments by text', async () => {
    await element(by.id('search-tab')).tap();
    
    // Type search query
    await element(by.id('search-input')).typeText('vacation');
    
    // Should show search results
    await waitFor(element(by.id('search-results')))
      .toBeVisible()
      .withTimeout(3000);
    
    // Should have at least one result
    await expect(element(by.id('search-result-0'))).toBeVisible();
    
    await takeScreenshot('search-results');
  });

  it('should filter by emotion', async () => {
    await element(by.id('search-tab')).tap();
    
    // Open filters
    await element(by.id('filter-button')).tap();
    
    // Select emotion filter
    await element(by.id('filter-emotion')).tap();
    await element(by.id('emotion-happy-filter')).tap();
    
    // Apply filter
    await element(by.id('apply-filters-button')).tap();
    
    // Should show filtered results
    await waitFor(element(by.id('search-results')))
      .toBeVisible()
      .withTimeout(3000);
    
    await takeScreenshot('filtered-by-emotion');
  });

  it('should filter by date range', async () => {
    await element(by.id('search-tab')).tap();
    
    // Open filters
    await element(by.id('filter-button')).tap();
    
    // Select date filter
    await element(by.id('filter-date')).tap();
    await element(by.id('date-range-this-week')).tap();
    
    // Apply filter
    await element(by.id('apply-filters-button')).tap();
    
    // Should show filtered results
    await waitFor(element(by.id('search-results')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should filter by tags', async () => {
    await element(by.id('search-tab')).tap();
    
    // Open filters
    await element(by.id('filter-button')).tap();
    
    // Select tag filter
    await element(by.id('filter-tags')).tap();
    await element(by.id('tag-family-filter')).tap();
    await element(by.id('tag-travel-filter')).tap();
    
    // Apply filter
    await element(by.id('apply-filters-button')).tap();
    
    // Should show filtered results
    await waitFor(element(by.id('search-results')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should clear search filters', async () => {
    await element(by.id('search-tab')).tap();
    
    // Apply some filters
    await element(by.id('filter-button')).tap();
    await element(by.id('filter-emotion')).tap();
    await element(by.id('emotion-happy-filter')).tap();
    await element(by.id('apply-filters-button')).tap();
    
    // Clear filters
    await element(by.id('filter-button')).tap();
    await element(by.id('clear-filters-button')).tap();
    
    // Should show all results
    await waitFor(element(by.id('search-results')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should show saved searches', async () => {
    await element(by.id('search-tab')).tap();
    
    // Search for something
    await element(by.id('search-input')).typeText('birthday');
    await waitFor(element(by.id('search-results')))
      .toBeVisible()
      .withTimeout(3000);
    
    // Save search
    await element(by.id('save-search-button')).tap();
    await element(by.id('search-name-input')).typeText('Birthday Moments');
    await element(by.id('confirm-save-button')).tap();
    
    // Check saved searches
    await element(by.id('saved-searches-button')).tap();
    await expect(element(by.text('Birthday Moments'))).toBeVisible();
    
    await takeScreenshot('saved-searches');
  });

  it('should infinite scroll timeline', async () => {
    await element(by.id('home-tab')).tap();
    
    // Scroll down multiple times
    for (let i = 0; i < 3; i++) {
      await element(by.id('moments-list')).scroll(300, 'down');
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    
    // Should have loaded more moments
    await expect(element(by.id('moment-card-20'))).toBeVisible();
  });
});
