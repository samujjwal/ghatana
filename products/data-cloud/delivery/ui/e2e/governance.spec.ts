import { test, expect } from '@playwright/test';
import { disableOnboardingWizard, dismissOnboardingWizard, mockGovernanceAPI } from './helpers/api-mocks';

/**
 * Trust Center E2E Tests
 */
test.describe('Trust Center', () => {
  test.beforeEach(async ({ page }) => {
    await disableOnboardingWizard(page);
    await mockGovernanceAPI(page);
    await page.goto('/trust');
    await dismissOnboardingWizard(page);
  });

  test('should display the Trust Center shell', async ({ page }) => {
    await expect(page.getByTestId('trust-center-page')).toBeVisible();
    await expect(page.locator('h1')).toContainText(/Trust Center/i);
  });

  test('should expose live governance quick actions', async ({ page }) => {
    await expect(page.getByTestId('trust-quick-action-classify-retention')).toBeVisible();
    await expect(page.getByTestId('trust-quick-action-redact-pii')).toBeVisible();
    await expect(page.getByTestId('trust-quick-action-purge-retention')).toBeVisible();
  });

  test('should surface lifecycle truth for live, read-only, and unavailable governance areas', async ({ page }) => {
    await expect(page.getByTestId('trust-lifecycle-section')).toBeVisible();
    await expect(page.getByTestId('trust-lifecycle-retention-operations')).toContainText(/Retention Operations/i);
    await expect(page.getByTestId('trust-lifecycle-access-review')).toContainText(/derived from audit and compliance summaries/i);
    await expect(page.getByTestId('trust-lifecycle-policy-lifecycle')).toContainText(/outside the current launcher-backed governance contract/i);
  });

  test('should surface derived governance recommendations and prefill the retention action', async ({ page }) => {
    await expect(page.getByTestId('trust-recommendation-recommend-retention-classification')).toBeVisible();
    await page.getByTestId('trust-recommendation-apply-recommend-retention-classification').click();
    await expect(page.getByTestId('trust-quick-action-dialog')).toBeVisible();
    await expect(page.getByTestId('trust-retention-reason')).toHaveValue(/Review 3 unclassified collections/i);
    await expect(page.getByTestId('trust-retention-pii-fields')).toHaveValue(/email, ssn/i);
  });

  test('should open the retention classification dialog', async ({ page }) => {
    await page.getByTestId('trust-quick-action-classify-retention').click();
    await expect(page.getByTestId('trust-quick-action-dialog')).toBeVisible();
    await expect(page.getByText(/Apply a live retention tier through the launcher governance contract/i)).toBeVisible();
  });

  test('should refresh compliance summary through the live trust center action', async ({ page }) => {
    await page.getByTestId('trust-quick-action-refresh-compliance').click();
    await expect(page.getByTestId('trust-action-summary')).toContainText(/Compliance summary refreshed/i);
    await expect(page.getByTestId('trust-action-summary')).toContainText(/operator review/i);
  });

  test('should preview and execute a retention purge with explicit confirmation state', async ({ page }) => {
    await page.getByTestId('trust-quick-action-purge-retention').click();
    await page.getByTestId('trust-purge-collection').fill('customers');
    await page.getByTestId('trust-quick-action-submit').click();

    await expect(page.getByTestId('trust-purge-preview')).toContainText(/Estimated rows: 24/i);
    await expect(page.getByTestId('trust-quick-action-submit')).toContainText(/Execute purge/i);

    await page.getByTestId('trust-quick-action-submit').click();
    await expect(page.getByTestId('trust-action-summary')).toContainText(/Retention purge completed/i);
    await expect(page.getByTestId('trust-action-summary')).toContainText(/24 deleted rows/i);
  });

  test('should disclose access review as read-only instead of pretending a live approval flow exists', async ({ page }) => {
    await page.getByTestId('trust-quick-action-access-review').click();
    await expect(page.getByTestId('trust-action-summary')).toContainText(/Access review remains read-only/i);
    await expect(page.getByTestId('trust-action-summary')).toContainText(/not yet available/i);
  });

  test('should render policies and audit evidence sections', async ({ page }) => {
    await expect(page.getByTestId('audit-timeline')).toBeVisible();
    await expect(page.getByTestId('policy-item').first()).toBeVisible();
  });

  test('should keep the governance alias routed to Trust Center', async ({ page }) => {
    await page.goto('/governance');
    await expect(page.getByTestId('trust-center-page')).toBeVisible();
    await expect(page.locator('h1')).toContainText(/Trust Center/i);
  });
});
