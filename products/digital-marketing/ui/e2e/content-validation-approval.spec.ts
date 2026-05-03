import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * E2E journey test: Content generation → Validation → Approval (DMOS-P2-003)
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end journey from content generation to approval
 * @doc.layer e2e
 */
test.describe('Content Generation → Validation → Approval Journey', () => {
  test('generate content, validate, and approve', async ({ page }) => {
    await loginAs(page, { roles: ['approver'] });

    // Navigate to content
    await page.goto('/content');
    await expect(page).toHaveURL(/\/content/);

    // Generate content
    await page.click('button:has-text("Generate Content")');
    await page.fill('[name="contentType"]', 'landing-page');
    await page.fill('[name="topic"]', 'Plumbing Services in SF');
    await page.click('button[type="submit"]');

    // Validate content
    await expect(page.locator('[data-testid="content-output"]')).toBeVisible();
    await page.click('button:has-text("Validate")');
    await expect(page.locator('[data-testid="validation-status"]')).toHaveText('VALID');

    // Submit for approval
    await page.click('button:has-text("Submit for Approval")');
    await expect(page.locator('[data-testid="approval-status"]')).toHaveText('PENDING');

    // Approve content
    await page.goto('/approvals');
    await page.click('[data-testid="approve-button"]');
    await page.fill('[name="comment"]', 'Content validated and approved');
    await page.click('button:has-text("Confirm Approval")');

    await expect(page.locator('[data-testid="approval-status"]')).toHaveText('APPROVED');
  });
});
