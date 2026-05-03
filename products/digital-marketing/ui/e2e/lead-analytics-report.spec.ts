import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * E2E journey test: Lead capture → Analytics event → Report (DMOS-P2-003)
 *
 * @doc.type test
 * @doc.purpose Verify end-to-end journey from lead capture to report generation
 * @doc.layer e2e
 */
test.describe('Lead Capture → Analytics Event → Report Journey', () => {
  test('capture lead, view analytics, and generate report', async ({ page }) => {
    await loginAs(page, { roles: ['approver'] });

    // Navigate to leads
    await page.goto('/leads');
    await expect(page).toHaveURL(/\/leads/);

    // Capture lead
    await page.click('button:has-text("Add Lead")');
    await page.fill('[name="name"]', 'John Doe');
    await page.fill('[name="email"]', 'john@example.com');
    await page.fill('[name="phone"]', '555-1234');
    await page.click('button[type="submit"]');

    // Verify lead captured
    await expect(page.locator('[data-testid="lead-status"]')).toHaveText('CAPTURED');

    // Navigate to analytics
    await page.goto('/analytics');
    await expect(page).toHaveURL(/\/analytics/);

    // View analytics events
    await expect(page.locator('[data-testid="analytics-events"]')).toBeVisible();

    // Generate report
    await page.click('button:has-text("Generate Report")');
    await page.fill('[name="reportType"]', 'weekly');
    await page.click('button[type="submit"]');

    // Verify report generated
    await expect(page.locator('[data-testid="report-output"]')).toBeVisible();
  });
});
