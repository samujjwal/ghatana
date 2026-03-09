/**
 * E2E Test: Timeline & Search Flow (Web)
 * Tests browsing, filtering, and searching moments
 */

import { test, expect, waitForApiResponse } from './fixtures';

test.describe('Timeline & Search Flow', () => {
  test('should display timeline with moments', async ({ authenticatedPage }) => {
    await expect(authenticatedPage.getByTestId('timeline')).toBeVisible();
    await expect(authenticatedPage.getByTestId('moment-card')).toHaveCount(await authenticatedPage.getByTestId('moment-card').count());
    
    await authenticatedPage.screenshot({ path: 'screenshots/web-timeline.png', fullPage: true });
  });

  test('should infinite scroll timeline', async ({ authenticatedPage }) => {
    const initialCount = await authenticatedPage.getByTestId('moment-card').count();
    
    // Scroll to bottom
    await authenticatedPage.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    
    // Wait for new moments to load
    await authenticatedPage.waitForTimeout(2000);
    
    const newCount = await authenticatedPage.getByTestId('moment-card').count();
    expect(newCount).toBeGreaterThan(initialCount);
  });

  test('should open moment detail', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('moment-card').first().click();
    
    await expect(authenticatedPage.getByTestId('moment-detail')).toBeVisible();
    await expect(authenticatedPage.getByTestId('moment-title')).toBeVisible();
    await expect(authenticatedPage.getByTestId('moment-content')).toBeVisible();
  });

  test('should search moments by text', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('search-input').fill('vacation');
    await authenticatedPage.getByTestId('search-button').click();
    
    await expect(authenticatedPage).toHaveURL(/search\?q=vacation/);
    await expect(authenticatedPage.getByTestId('search-results')).toBeVisible();
  });

  test('should filter by emotion', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('filter-button').click();
    await authenticatedPage.getByTestId('filter-emotion').click();
    await authenticatedPage.getByTestId('emotion-happy-filter').click();
    await authenticatedPage.getByTestId('apply-filters').click();
    
    // Should update URL with filter params
    await expect(authenticatedPage).toHaveURL(/emotion=happy/);
    
    // All visible moments should have happy emotion
    const emotionBadges = authenticatedPage.getByTestId('moment-emotion');
    await expect(emotionBadges.first()).toContainText('Happy');
  });

  test('should filter by date range', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('filter-button').click();
    await authenticatedPage.getByTestId('filter-date').click();
    
    // Select date range
    await authenticatedPage.getByTestId('date-range-this-week').click();
    await authenticatedPage.getByTestId('apply-filters').click();
    
    await expect(authenticatedPage).toHaveURL(/dateRange=this-week/);
  });

  test('should filter by tags', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('filter-button').click();
    await authenticatedPage.getByTestId('filter-tags').click();
    
    // Select tags
    await authenticatedPage.getByTestId('tag-family-filter').click();
    await authenticatedPage.getByTestId('tag-travel-filter').click();
    await authenticatedPage.getByTestId('apply-filters').click();
    
    await expect(authenticatedPage).toHaveURL(/tags=family,travel/);
  });

  test('should clear filters', async ({ authenticatedPage }) => {
    // Apply some filters
    await authenticatedPage.getByTestId('filter-button').click();
    await authenticatedPage.getByTestId('filter-emotion').click();
    await authenticatedPage.getByTestId('emotion-happy-filter').click();
    await authenticatedPage.getByTestId('apply-filters').click();
    
    // Clear filters
    await authenticatedPage.getByTestId('clear-filters').click();
    
    await expect(authenticatedPage).toHaveURL('/');
  });

  test('should save search', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('search-input').fill('birthday');
    await authenticatedPage.getByTestId('search-button').click();
    
    // Save search
    await authenticatedPage.getByTestId('save-search-button').click();
    await authenticatedPage.getByTestId('search-name-input').fill('Birthday Moments');
    await authenticatedPage.getByTestId('confirm-save-button').click();
    
    await expect(authenticatedPage.getByText(/search saved/i)).toBeVisible();
  });

  test('should load saved search', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('saved-searches-button').click();
    
    await expect(authenticatedPage.getByTestId('saved-searches-dropdown')).toBeVisible();
    
    // Click saved search
    await authenticatedPage.getByTestId('saved-search-0').click();
    
    // Should load search results
    await expect(authenticatedPage.getByTestId('search-results')).toBeVisible();
  });

  test('should sort timeline', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('sort-select').click();
    await authenticatedPage.getByTestId('sort-oldest').click();
    
    // Should update URL
    await expect(authenticatedPage).toHaveURL(/sort=oldest/);
  });

  test('should toggle grid/list view', async ({ authenticatedPage }) => {
    // Default should be grid
    await expect(authenticatedPage.getByTestId('timeline')).toHaveClass(/grid/);
    
    // Switch to list
    await authenticatedPage.getByTestId('view-list').click();
    await expect(authenticatedPage.getByTestId('timeline')).toHaveClass(/list/);
    
    // Switch back to grid
    await authenticatedPage.getByTestId('view-grid').click();
    await expect(authenticatedPage.getByTestId('timeline')).toHaveClass(/grid/);
  });

  test('should show search suggestions', async ({ authenticatedPage }) => {
    const searchInput = authenticatedPage.getByTestId('search-input');
    await searchInput.fill('vac');
    
    // Should show suggestions dropdown
    await expect(authenticatedPage.getByTestId('search-suggestions')).toBeVisible();
    
    // Select suggestion
    await authenticatedPage.getByTestId('suggestion-0').click();
    
    // Should perform search
    await expect(authenticatedPage.getByTestId('search-results')).toBeVisible();
  });

  test('should highlight search terms', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('search-input').fill('vacation');
    await authenticatedPage.getByTestId('search-button').click();
    
    // Search term should be highlighted in results
    const highlighted = authenticatedPage.locator('mark, .highlight').first();
    await expect(highlighted).toContainText('vacation');
  });
});
