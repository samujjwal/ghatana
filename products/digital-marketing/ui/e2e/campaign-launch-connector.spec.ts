import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * E2E journey test: Campaign preflight → Launch command → Connector execution (DMOS-P2-003)
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end journey from campaign preflight to connector execution
 * @doc.layer e2e
 */
test.describe('Campaign Preflight → Launch Command → Connector Execution Journey', () => {
  test('run preflight, launch campaign, and execute connector', async ({ page }) => {
    await loginAs(page, { roles: ['approver'] });

    // Navigate to campaigns
    await page.goto('/campaigns');
    await expect(page).toHaveURL(/\/campaigns/);

    // Run preflight check
    await page.click('button:has-text("Run Preflight")');
    await expect(page.locator('[data-testid="preflight-status"]')).toHaveText('PASS');

    // Launch campaign
    await page.click('button:has-text("Launch Campaign")');
    await page.fill('[name="campaignName"]', 'Q3 SF Plumbing Campaign');
    await page.click('button[type="submit"]');

    // Verify command created
    await expect(page.locator('[data-testid="command-status"]')).toHaveText('CREATED');

    // Execute connector
    await page.click('button:has-text("Execute Connector")');
    await expect(page.locator('[data-testid="connector-status"]')).toHaveText('RUNNING');

    // Wait for completion
    await expect(page.locator('[data-testid="connector-status"]')).toHaveText('COMPLETED', { timeout: 10000 });
  });
});
