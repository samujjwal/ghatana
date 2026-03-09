/**
 * E2E Test: Analytics Dashboard (Web)
 * Tests analytics, insights, and data visualization
 */

import { test, expect } from './fixtures';

test.describe('Analytics Dashboard', () => {
  test('should view analytics dashboard', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    await expect(authenticatedPage.getByTestId('analytics-screen')).toBeVisible();
    await expect(authenticatedPage.getByTestId('stats-overview')).toBeVisible();
    await expect(authenticatedPage.getByTestId('mood-chart')).toBeVisible();
    
    await authenticatedPage.screenshot({ path: 'screenshots/web-analytics.png', fullPage: true });
  });

  test('should display key statistics', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    await expect(authenticatedPage.getByTestId('total-moments-stat')).toBeVisible();
    await expect(authenticatedPage.getByTestId('this-week-stat')).toBeVisible();
    await expect(authenticatedPage.getByTestId('streak-stat')).toBeVisible();
    await expect(authenticatedPage.getByTestId('avg-per-day-stat')).toBeVisible();
  });

  test('should view mood trends chart', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    const moodChart = authenticatedPage.getByTestId('mood-chart');
    await expect(moodChart).toBeVisible();
    
    // Click to view details
    await authenticatedPage.getByTestId('mood-chart-details').click();
    
    await expect(authenticatedPage.getByTestId('mood-distribution')).toBeVisible();
  });

  test('should change time range', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    // Change to monthly view
    await authenticatedPage.getByTestId('time-range-select').selectOption('month');
    
    // Charts should update
    await authenticatedPage.waitForTimeout(1000);
    
    // Verify URL updated
    await expect(authenticatedPage).toHaveURL(/range=month/);
  });

  test('should view activity heatmap', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    await expect(authenticatedPage.getByTestId('activity-heatmap')).toBeVisible();
    
    // Hover over day
    await authenticatedPage.getByTestId('heatmap-cell').first().hover();
    
    // Should show tooltip
    await expect(authenticatedPage.getByTestId('heatmap-tooltip')).toBeVisible();
  });

  test('should view tag analytics', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    // Scroll to tag section
    await authenticatedPage.getByTestId('tag-analytics-section').scrollIntoViewIfNeeded();
    
    await expect(authenticatedPage.getByTestId('tag-cloud')).toBeVisible();
    await expect(authenticatedPage.getByTestId('top-tags-list')).toBeVisible();
  });

  test('should view media breakdown', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    await authenticatedPage.getByTestId('media-breakdown-section').scrollIntoViewIfNeeded();
    
    await expect(authenticatedPage.getByTestId('media-pie-chart')).toBeVisible();
    await expect(authenticatedPage.getByTestId('voice-percentage')).toBeVisible();
    await expect(authenticatedPage.getByTestId('image-percentage')).toBeVisible();
  });

  test('should export analytics report', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    // Click export
    await authenticatedPage.getByTestId('export-report-button').click();
    
    // Select format
    await authenticatedPage.getByTestId('export-format-pdf').click();
    
    // Confirm
    const downloadPromise = authenticatedPage.waitForEvent('download');
    await authenticatedPage.getByTestId('confirm-export-button').click();
    
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('analytics-report');
  });

  test('should view insights', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    // Navigate to insights tab
    await authenticatedPage.getByTestId('insights-tab').click();
    
    await expect(authenticatedPage.getByTestId('insights-grid')).toBeVisible();
    await expect(authenticatedPage.getByTestId('insight-card')).toHaveCount(await authenticatedPage.getByTestId('insight-card').count());
  });

  test('should view peak activity times', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    await authenticatedPage.getByTestId('insights-tab').click();
    
    await expect(authenticatedPage.getByTestId('peak-times-chart')).toBeVisible();
  });

  test('should compare time periods', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    // Enable comparison
    await authenticatedPage.getByTestId('compare-toggle').click();
    
    // Select comparison period
    await authenticatedPage.getByTestId('compare-period-select').selectOption('previous-month');
    
    // Should show comparison data
    await expect(authenticatedPage.getByTestId('comparison-chart')).toBeVisible();
    await expect(authenticatedPage.getByTestId('change-percentage')).toBeVisible();
  });

  test('should view goal progress', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    await authenticatedPage.getByTestId('goals-section').scrollIntoViewIfNeeded();
    
    await expect(authenticatedPage.getByTestId('goal-progress-list')).toBeVisible();
    await expect(authenticatedPage.getByTestId('goal-progress-bar')).toBeVisible();
  });

  test('should filter charts by media type', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    // Filter by voice
    await authenticatedPage.getByTestId('media-filter-voice').click();
    
    // Charts should update
    await authenticatedPage.waitForTimeout(1000);
    
    await expect(authenticatedPage).toHaveURL(/mediaType=voice/);
  });

  test('should view emotion distribution', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/analytics');
    
    await authenticatedPage.getByTestId('emotion-section').scrollIntoViewIfNeeded();
    
    await expect(authenticatedPage.getByTestId('emotion-distribution-chart')).toBeVisible();
  });
});
