import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * E2E journey test: Strategy → Budget → Proposal/SOW → Approval (DMOS-P2-003)
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end journey from strategy to proposal approval
 * @doc.layer e2e
 */
test.describe('Strategy → Budget → Proposal/SOW → Approval Journey', () => {
  test('create budget, generate proposal, and approve', async ({ page }) => {
    await loginAs(page, { roles: ['approver'] });

    // Navigate to strategy
    await page.goto('/strategy');
    await expect(page).toHaveURL(/\/strategy/);

    // Create budget from strategy
    await page.click('button:has-text("Create Budget")');
    await page.fill('[name="amount"]', '10000');
    await page.fill('[name="period"]', 'monthly');
    await page.click('button[type="submit"]');

    // Navigate to proposal
    await page.goto('/proposal');
    await expect(page).toHaveURL(/\/proposal/);

    // Generate proposal
    await page.click('button:has-text("Generate Proposal")');
    await expect(page.locator('[data-testid="proposal-output"]')).toBeVisible();

    // Generate SOW
    await page.click('button:has-text("Generate SOW")');
    await expect(page.locator('[data-testid="sow-output"]')).toBeVisible();

    // Submit for approval
    await page.click('button:has-text("Submit for Approval")');
    await expect(page.locator('[data-testid="approval-status"]')).toHaveText('PENDING');

    // Approve
    await page.goto('/approvals');
    await page.click('[data-testid="approve-button"]');
    await page.fill('[name="comment"]', 'Proposal and SOW approved');
    await page.click('button:has-text("Confirm Approval")');

    await expect(page.locator('[data-testid="approval-status"]')).toHaveText('APPROVED');
  });
});
