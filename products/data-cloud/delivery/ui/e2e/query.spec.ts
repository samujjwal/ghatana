import { test, expect } from '@playwright/test';
import { disableOnboardingWizard, dismissOnboardingWizard, mockCollectionsAPI, mockQueryWorkspaceAPI } from './helpers/api-mocks';

/**
 * SQL workspace E2E tests
 */
test.describe('SQL Workspace', () => {
  test.beforeEach(async ({ page }) => {
    await disableOnboardingWizard(page);
    await mockCollectionsAPI(page);
    await mockQueryWorkspaceAPI(page);
    await page.goto('/query');
    await dismissOnboardingWizard(page);
  });

  test('should display the canonical SQL workspace shell', async ({ page }) => {
    await expect(page.getByTestId('sql-workspace-page')).toBeVisible();
    await expect(page.getByTestId('sql-workspace-header')).toBeVisible();
    await expect(page.getByTestId('sql-recommendation-panel')).toBeVisible();
    await expect(page.getByTestId('sql-editor-panel')).toBeVisible();
    await expect(page.getByTestId('sql-results-panel')).toBeVisible();
  });

  test('should open AI assist and render inferred scope guidance', async ({ page }) => {
    await page.getByTestId('sql-ai-assist-toggle').click();
    await expect(page.getByTestId('sql-ai-assist-panel')).toBeVisible();
    await page.getByTestId('sql-ai-assist-input').fill('Show top products this week');
    await page.getByTestId('sql-ai-assist-generate').click();
    await expect(page.getByTestId('sql-inferred-scope')).toBeVisible();
    await expect(page.getByTestId('sql-apply-suggestion')).toBeVisible();
  });

  test('should execute a direct analytics query and render tabular results', async ({ page }) => {
    await page.getByTestId('sql-run-query').click();
    await expect(page.getByTestId('sql-query-results')).toBeVisible();
    await expect(page.locator('text=/1 rows • 22ms/i')).toBeVisible();
    await expect(page.locator('text=/Product A/i')).toBeVisible();
  });

  test('should explain the current query and surface plan guardrails', async ({ page }) => {
    await page.getByTestId('sql-explain-query').click();
    await expect(page.getByTestId('sql-query-plan')).toBeVisible();
    await expect(page.getByTestId('sql-query-plan-guardrails')).toContainText(/Optimizer hints available/i);
    await expect(page.locator('text=/cost 144/i')).toBeVisible();
  });
});