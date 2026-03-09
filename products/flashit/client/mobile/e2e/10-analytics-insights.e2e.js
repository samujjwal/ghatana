/**
 * E2E Test: Analytics & Insights
 * Tests analytics dashboard and insights features
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, scrollToElement, takeScreenshot } = require('./helpers/setup');

describe('Analytics & Insights Flow', () => {
  beforeAll(async () => {
    await device.launchApp();
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('should view analytics dashboard', async () => {
    await element(by.id('analytics-tab')).tap();
    
    await expect(element(by.id('analytics-screen'))).toBeVisible();
    await expect(element(by.id('stats-overview'))).toBeVisible();
    await expect(element(by.id('mood-chart'))).toBeVisible();
    
    await takeScreenshot('analytics-dashboard');
  });

  it('should view moment statistics', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Should show total moments
    await expect(element(by.id('total-moments-stat'))).toBeVisible();
    
    // Should show this week's count
    await expect(element(by.id('this-week-stat'))).toBeVisible();
    
    // Should show streak
    await expect(element(by.id('streak-stat'))).toBeVisible();
  });

  it('should view mood trends', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Should show mood chart
    await expect(element(by.id('mood-chart'))).toBeVisible();
    
    // Tap to view details
    await element(by.id('mood-chart-details-button')).tap();
    
    await expect(element(by.id('mood-trends-screen'))).toBeVisible();
    await expect(element(by.id('mood-distribution'))).toBeVisible();
    
    await takeScreenshot('mood-trends');
  });

  it('should change analytics time range', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Change to monthly view
    await element(by.id('time-range-select')).tap();
    await element(by.id('range-month')).tap();
    
    // Should update charts
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    await takeScreenshot('analytics-monthly');
  });

  it('should view activity heatmap', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Scroll to heatmap
    await scrollToElement('analytics-scroll', by.id('activity-heatmap'));
    
    await expect(element(by.id('activity-heatmap'))).toBeVisible();
    
    // Tap on a day
    await element(by.id('heatmap-day-2024-01-15')).tap();
    
    // Should show day details
    await expect(element(by.text('5 moments'))).toBeVisible();
  });

  it('should view tag analytics', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Navigate to tags section
    await scrollToElement('analytics-scroll', by.id('tag-analytics'));
    
    await expect(element(by.id('tag-analytics'))).toBeVisible();
    await expect(element(by.id('top-tags-list'))).toBeVisible();
    
    await takeScreenshot('tag-analytics');
  });

  it('should view media type breakdown', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Scroll to media breakdown
    await scrollToElement('analytics-scroll', by.id('media-breakdown'));
    
    await expect(element(by.id('media-breakdown'))).toBeVisible();
    await expect(element(by.id('voice-percentage'))).toBeVisible();
    await expect(element(by.id('image-percentage'))).toBeVisible();
    await expect(element(by.id('video-percentage'))).toBeVisible();
  });

  it('should export analytics report', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Export report
    await element(by.id('export-report-button')).tap();
    
    // Select format
    await element(by.id('format-pdf')).tap();
    
    // Confirm export
    await element(by.id('confirm-export-button')).tap();
    
    await waitFor(element(by.text('Report generated')))
      .toBeVisible()
      .withTimeout(5000);
  });

  it('should view insights', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Navigate to insights tab
    await element(by.id('insights-tab-button')).tap();
    
    await expect(element(by.id('insights-screen'))).toBeVisible();
    await expect(element(by.id('insight-card-0'))).toBeVisible();
    
    await takeScreenshot('insights');
  });

  it('should view peak activity times', async () => {
    await element(by.id('analytics-tab')).tap();
    await element(by.id('insights-tab-button')).tap();
    
    // Find peak times insight
    await scrollToElement('insights-list', by.id('insight-peak-times'));
    
    await expect(element(by.id('insight-peak-times'))).toBeVisible();
    await expect(element(by.text('You're most active in the evening'))).toBeVisible();
  });

  it('should compare time periods', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Enable comparison mode
    await element(by.id('compare-button')).tap();
    
    // Select previous month
    await element(by.id('compare-period-select')).tap();
    await element(by.id('compare-previous-month')).tap();
    
    // Should show comparison data
    await expect(element(by.id('comparison-chart'))).toBeVisible();
    await expect(element(by.id('change-percentage'))).toBeVisible();
    
    await takeScreenshot('period-comparison');
  });

  it('should view goal progress', async () => {
    await element(by.id('analytics-tab')).tap();
    
    // Scroll to goals section
    await scrollToElement('analytics-scroll', by.id('goals-section'));
    
    await expect(element(by.id('goals-section'))).toBeVisible();
    await expect(element(by.id('goal-daily-capture'))).toBeVisible();
    
    // Should show progress percentage
    await expect(element(by.id('goal-progress-bar'))).toBeVisible();
  });
});
