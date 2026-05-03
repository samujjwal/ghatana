import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * E2E journey test: Failed connector → DLQ → Retry/Recovery (DMOS-P2-003)
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end journey for failed connector recovery
 * @doc.layer e2e
 */
test.describe('Failed Connector → DLQ → Retry/Recovery Journey', () => {
  test('handle connector failure, send to DLQ, and retry', async ({ page }) => {
    await loginAs(page, { roles: ['admin'] });

    // Navigate to connectors
    await page.goto('/connectors');
    await expect(page).toHaveURL(/\/connectors/);

    // Execute connector that will fail
    await page.click('button:has-text("Execute Connector (Test Failure)")');
    await expect(page.locator('[data-testid="connector-status"]')).toHaveText('RUNNING');

    // Wait for failure
    await expect(page.locator('[data-testid="connector-status"]')).toHaveText('FAILED', { timeout: 10000 });
    await expect(page.locator('[data-testid="error-message"]')).toBeVisible();

    // Verify sent to DLQ
    await page.goto('/dlq');
    await expect(page).toHaveURL(/\/dlq/);
    await expect(page.locator('[data-testid="dlq-item"]')).toBeVisible();

    // Retry from DLQ
    await page.click('[data-testid="retry-button"]');

    // Verify retry succeeded
    await expect(page.locator('[data-testid="connector-status"]')).toHaveText('COMPLETED', { timeout: 10000 });

    // Navigate back to connectors
    await page.goto('/connectors');
    await expect(page.locator('[data-testid="connector-status"]')).toHaveText('COMPLETED');
  });
});
