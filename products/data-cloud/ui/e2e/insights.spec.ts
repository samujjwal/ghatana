import { test, expect } from '@playwright/test';
import { disableOnboardingWizard, mockInsightsAPI } from './helpers/api-mocks';

test.describe('Insights', () => {
  test.beforeEach(async ({ page }) => {
    await disableOnboardingWizard(page);
    await mockInsightsAPI(page);
  });

  test('should surface AI truth telemetry for operators', async ({ page }) => {
    await page.goto('/insights');

    await expect(page.getByText('Operator Diagnostics')).toBeVisible();
    await expect(page.getByTestId('insights-ai-truth-panel')).toBeVisible();
    await expect(page.getByText('AI Truth Snapshot')).toBeVisible();
    await expect(page.getByText('2/6 fallbacks')).toBeVisible();
    await expect(page.getByTestId('insights-ai-type-analytics_suggest')).toContainText('Analytics suggestions');
    await expect(page.getByTestId('insights-ai-type-analytics_suggest')).toContainText('3 requests');
    await expect(page.getByTestId('insights-ai-type-pipeline_draft')).toContainText('Workflow draft generation');
    await expect(page.getByTestId('insights-ai-type-pipeline_draft')).toContainText('50% fallback');
    await expect(page.getByTestId('insights-ai-type-pipeline_draft')).toContainText('1 fallback responses');
    await expect(page.getByText('Review low-confidence drafts or any fallback-generated workflow before saving.')).toBeVisible();
  });
});