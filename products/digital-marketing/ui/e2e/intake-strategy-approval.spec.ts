import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * E2E journey test: Intake → Strategy → Approval (DMOS-P2-003)
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end journey from intake to strategy approval
 * @doc.layer e2e
 */
test.describe('Intake → Strategy → Approval Journey', () => {
  test('complete intake, generate strategy, and approve', async ({ page }) => {
    await loginAs(page, { roles: ['approver'] });

    // Navigate to intake
    await page.goto('/intake');
    await expect(page).toHaveURL(/\/intake/);

    // Fill intake form
    await page.fill('[name="serviceArea"]', 'San Francisco');
    await page.fill('[name="primaryOffer"]', 'Plumbing Services');
    await page.fill('[name="monthlyBudget"]', '5000');
    await page.click('button[type="submit"]');

    // Navigate to strategy
    await page.goto('/strategy');
    await expect(page).toHaveURL(/\/strategy/);

    // Generate strategy
    await page.click('button:has-text("Generate Strategy")');
    await expect(page.locator('[data-testid="strategy-output"]')).toBeVisible();

    // Submit for approval
    await page.click('button:has-text("Submit for Approval")');
    await expect(page.locator('[data-testid="approval-status"]')).toHaveText('PENDING');

    // Approve strategy
    await page.goto('/approvals');
    await expect(page).toHaveURL(/\/approvals/);

    await page.click('[data-testid="approve-button"]');
    await page.fill('[name="comment"]', 'Approved for execution');
    await page.click('button:has-text("Confirm Approval")');

    await expect(page.locator('[data-testid="approval-status"]')).toHaveText('APPROVED');
  });
});
