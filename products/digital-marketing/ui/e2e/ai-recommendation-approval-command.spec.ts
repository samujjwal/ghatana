import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * E2E journey test: AI recommendation → Approval → Command (DMOS-P2-003)
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end journey from AI recommendation to command execution
 * @doc.layer e2e
 */
test.describe('AI Recommendation → Approval → Command Journey', () => {
  test('receive recommendation, approve, and execute command', async ({ page }) => {
    await loginAs(page, { roles: ['approver'] });

    // Navigate to AI recommendations
    await page.goto('/ai-recommendations');
    await expect(page).toHaveURL(/\/ai-recommendations/);

    // View recommendation
    await expect(page.locator('[data-testid="recommendation-list"]')).toBeVisible();
    await page.click('[data-testid="recommendation-item"]');

    // View recommendation details
    await expect(page.locator('[data-testid="recommendation-details"]')).toBeVisible();
    await expect(page.locator('[data-testid="confidence-score"]')).toBeVisible();

    // Approve recommendation
    await page.click('button:has-text("Approve Recommendation")');
    await page.fill('[name="comment"]', 'Recommendation approved for execution');

    // Verify command created
    await page.click('button:has-text("Confirm Approval")');
    await expect(page.locator('[data-testid="command-status"]')).toHaveText('CREATED');

    // Execute command
    await page.click('button:has-text("Execute Command")');
    await expect(page.locator('[data-testid="command-status"]')).toHaveText('RUNNING');

    // Wait for completion
    await expect(page.locator('[data-testid="command-status"]')).toHaveText('COMPLETED', { timeout: 10000 });
  });
});
