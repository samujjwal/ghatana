import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * E2E journey test: Kill switch blocks connector (DMOS-P2-003)
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end journey for kill switch blocking connector execution
 * @doc.layer e2e
 */
test.describe('Kill Switch Blocks Connector Journey', () => {
  test('activate kill switch and verify connector blocked', async ({ page }) => {
    await loginAs(page, { roles: ['admin'] });

    // Navigate to connectors
    await page.goto('/connectors');
    await expect(page).toHaveURL(/\/connectors/);

    // Activate kill switch
    await page.click('button:has-text("Activate Kill Switch")');
    await page.fill('[name="reason"]', 'Emergency stop due to compliance issue');
    await page.click('button[type="submit"]');

    // Verify kill switch active
    await expect(page.locator('[data-testid="kill-switch-status"]')).toHaveText('ACTIVE');

    // Try to execute connector
    await page.click('button:has-text("Execute Connector")');

    // Verify blocked
    await expect(page.locator('[data-testid="connector-status"]')).toHaveText('BLOCKED');
    await expect(page.locator('[data-testid="block-reason"]')).toHaveText('Emergency stop due to compliance issue');

    // Deactivate kill switch
    await page.click('button:has-text("Deactivate Kill Switch")');
    await expect(page.locator('[data-testid="kill-switch-status"]')).toHaveText('INACTIVE');
  });
});
