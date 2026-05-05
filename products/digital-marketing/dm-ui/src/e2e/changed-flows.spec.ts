import { test, expect, Page } from '@playwright/test';
import { generateUniqueId } from './utils/test-helpers';

/**
 * P1-044: Changed-flow browser E2E tests
 *
 * Comprehensive browser tests for modification workflows:
 * - Campaign modification UI
 * - Budget change approval flow
 * - Strategy update workflow
 * - Change history visualization
 * - Approval/rejection UI
 * - Rollback scenarios
 */

test.describe('P1-044: Changed-Flow Browser E2E Tests', () => {
  const workspaceId = 'test-workspace-changed-flows';
  let campaignId: string;

  test.beforeEach(async ({ page }) => {
    // Login and navigate to workspace
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', 'test-reviewer@example.com');
    await page.fill('[data-testid="password-input"]', 'test-password');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(`/${workspaceId}/campaigns`);
  });

  test('P1-044: Campaign modification triggers approval workflow', async ({ page }) => {
    // Create a campaign first
    campaignId = await createCampaign(page, {
      name: 'Test Campaign for Changes',
      description: 'Testing modification workflows',
      status: 'DRAFT'
    });

    // Navigate to campaign detail
    await page.goto(`/${workspaceId}/campaigns/${campaignId}`);
    await page.waitForSelector('[data-testid="campaign-detail-page"]');

    // Click edit button
    await page.click('[data-testid="edit-campaign-button"]');
    await page.waitForSelector('[data-testid="campaign-edit-form"]');

    // Modify campaign name
    const newName = 'Test Campaign for Changes - Modified';
    await page.fill('[data-testid="campaign-name-input"]', newName);
    
    // Add modification reason
    await page.fill(
      '[data-testid="modification-reason-input"]',
      'Updated campaign name to better reflect the target audience'
    );

    // Save changes
    await page.click('[data-testid="save-campaign-button"]');

    // Verify modification pending state
    await page.waitForSelector('[data-testid="modification-pending-banner"]');
    const bannerText = await page.textContent('[data-testid="modification-pending-banner"]');
    expect(bannerText).toContain('pending approval');

    // Verify campaign shows pending changes indicator
    const statusBadge = await page.textContent('[data-testid="campaign-status-badge"]');
    expect(statusBadge).toBe('MODIFICATION_PENDING');
  });

  test('P1-044: Budget change requires approval', async ({ page }) => {
    // Create campaign with budget
    campaignId = await createCampaign(page, {
      name: 'Budget Change Test Campaign',
      status: 'APPROVED',
      budget: { totalBudget: 50000, currency: 'USD' }
    });

    // Navigate to budget section
    await page.goto(`/${workspaceId}/campaigns/${campaignId}/budget`);
    await page.waitForSelector('[data-testid="budget-detail-page"]');

    // Click edit budget
    await page.click('[data-testid="edit-budget-button"]');
    await page.waitForSelector('[data-testid="budget-edit-form"]');

    // Modify budget amount
    await page.fill('[data-testid="budget-amount-input"]', '75000');
    await page.fill(
      '[data-testid="budget-change-reason-input"]',
      'Increased budget for expanded social media reach'
    );

    // Submit for approval
    await page.click('[data-testid="submit-budget-button"]');

    // Verify approval workflow triggered
    await page.waitForSelector('[data-testid="approval-pending-modal"]');
    const modalText = await page.textContent('[data-testid="approval-pending-modal"]');
    expect(modalText).toContain('Budget modification');
    expect(modalText).toContain('pending approval');

    // Verify old and new amounts shown
    const oldAmount = await page.textContent('[data-testid="old-budget-amount"]');
    const newAmount = await page.textContent('[data-testid="new-budget-amount"]');
    expect(oldAmount).toContain('$50,000');
    expect(newAmount).toContain('$75,000');
  });

  test('P1-044: Strategy update creates new version', async ({ page }) => {
    // Create campaign with approved strategy
    campaignId = await createCampaign(page, {
      name: 'Strategy Update Test',
      status: 'APPROVED',
      hasApprovedStrategy: true
    });

    // Navigate to strategy section
    await page.goto(`/${workspaceId}/campaigns/${campaignId}/strategy`);
    await page.waitForSelector('[data-testid="strategy-detail-page"]');

    // Click regenerate strategy
    await page.click('[data-testid="regenerate-strategy-button"]');
    await page.waitForSelector('[data-testid="strategy-generation-dialog"]');

    // Modify strategy parameters
    await page.click('[data-testid="goal-awareness-checkbox"]');
    await page.click('[data-testid="goal-retention-checkbox"]');
    
    await page.fill(
      '[data-testid="strategy-context-input"]',
      'Focus on customer retention for Q2'
    );

    // Generate new strategy
    await page.click('[data-testid="generate-strategy-button"]');

    // Wait for generation and approval prompt
    await page.waitForSelector('[data-testid="strategy-generated-card"]', { timeout: 30000 });
    await page.waitForSelector('[data-testid="strategy-approval-required-banner"]');

    // Verify version indicator
    const versionText = await page.textContent('[data-testid="strategy-version-badge"]');
    expect(versionText).toContain('Version 2');
    expect(versionText).toContain('Pending Approval');
  });

  test('P1-044: Approval UI shows change diff', async ({ page }) => {
    // Setup: create campaign with pending modification
    campaignId = await createCampaignWithPendingChange(page, {
      name: 'Diff Test Campaign',
      modifications: {
        name: 'Diff Test Campaign - Modified',
        description: 'Updated description'
      }
    });

    // Navigate as reviewer
    await page.goto(`/${workspaceId}/approvals`);
    await page.waitForSelector('[data-testid="approvals-list-page"]');

    // Find and click on pending approval
    const approvalCard = page.locator('[data-testid="approval-card"]', {
      hasText: campaignId
    });
    await approvalCard.click();

    // Wait for approval detail
    await page.waitForSelector('[data-testid="approval-detail-page"]');

    // Verify change diff is shown
    await page.waitForSelector('[data-testid="change-diff-section"]');

    // Check modified fields are highlighted
    const nameChange = await page.textContent('[data-testid="diff-name"]');
    expect(nameChange).toContain('Diff Test Campaign');
    expect(nameChange).toContain('Diff Test Campaign - Modified');

    const descriptionChange = await page.textContent('[data-testid="diff-description"]');
    expect(descriptionChange).toContain('Updated description');

    // Verify visual indicators
    await expect(page.locator('[data-testid="diff-removed-value"]')).toHaveClass(/removed/);
    await expect(page.locator('[data-testid="diff-added-value"]')).toHaveClass(/added/);
  });

  test('P1-044: Approve modification from UI', async ({ page }) => {
    // Setup pending modification
    campaignId = await createCampaignWithPendingChange(page, {
      name: 'Approve Test Campaign',
      modifications: { name: 'Approve Test Campaign - Approved' }
    });

    const approvalId = await getPendingApprovalId(page, campaignId);

    // Navigate to approval
    await page.goto(`/${workspaceId}/approvals/${approvalId}`);
    await page.waitForSelector('[data-testid="approval-detail-page"]');

    // Review changes
    await expect(page.locator('[data-testid="change-diff-section"]')).toBeVisible();

    // Enter approval comment
    await page.fill(
      '[data-testid="approval-comment-input"]',
      'Campaign modification approved - aligns with marketing objectives'
    );

    // Click approve
    await page.click('[data-testid="approve-button"]');

    // Verify success
    await page.waitForSelector('[data-testid="approval-success-message"]');
    const successText = await page.textContent('[data-testid="approval-success-message"]');
    expect(successText).toContain('approved successfully');

    // Navigate to campaign and verify updated
    await page.goto(`/${workspaceId}/campaigns/${campaignId}`);
    await page.waitForSelector('[data-testid="campaign-detail-page"]');

    const campaignName = await page.textContent('[data-testid="campaign-name-display"]');
    expect(campaignName).toBe('Approve Test Campaign - Approved');

    // Verify status is APPROVED (not MODIFICATION_PENDING)
    const statusBadge = await page.textContent('[data-testid="campaign-status-badge"]');
    expect(statusBadge).toBe('APPROVED');
  });

  test('P1-044: Reject modification restores previous state', async ({ page }) => {
    // Setup pending modification
    campaignId = await createCampaignWithPendingChange(page, {
      name: 'Reject Test Campaign',
      originalName: 'Reject Test Campaign - Original',
      modifications: { name: 'Reject Test Campaign - Rejected Change' }
    });

    const approvalId = await getPendingApprovalId(page, campaignId);

    // Navigate to approval
    await page.goto(`/${workspaceId}/approvals/${approvalId}`);
    await page.waitForSelector('[data-testid="approval-detail-page"]');

    // Enter rejection reason
    await page.fill(
      '[data-testid="rejection-reason-input"]',
      'Name change does not align with brand guidelines'
    );

    // Click reject
    await page.click('[data-testid="reject-button"]');

    // Verify rejection success
    await page.waitForSelector('[data-testid="rejection-success-message"]');

    // Navigate to campaign and verify original name restored
    await page.goto(`/${workspaceId}/campaigns/${campaignId}`);
    await page.waitForSelector('[data-testid="campaign-detail-page"]');

    const campaignName = await page.textContent('[data-testid="campaign-name-display"]');
    expect(campaignName).toBe('Reject Test Campaign - Original');

    // Verify no pending changes indicator
    await expect(page.locator('[data-testid="modification-pending-banner"]')).not.toBeVisible();
  });

  test('P1-044: Change history shows all modifications', async ({ page }) => {
    // Create campaign with multiple changes
    campaignId = await createCampaignWithChangeHistory(page, {
      name: 'History Test Campaign',
      changes: [
        { field: 'name', from: 'Original', to: 'Change 1' },
        { field: 'name', from: 'Change 1', to: 'Change 2' },
        { field: 'description', from: 'Desc 1', to: 'Desc 2' }
      ]
    });

    // Navigate to campaign history
    await page.goto(`/${workspaceId}/campaigns/${campaignId}/history`);
    await page.waitForSelector('[data-testid="campaign-history-page"]');

    // Verify history items
    const historyItems = page.locator('[data-testid="history-item"]');
    await expect(historyItems).toHaveCount(4); // Creation + 3 modifications

    // Check first item is creation
    const firstItemType = await page.textContent('[data-testid="history-item"]:first-child [data-testid="change-type"]');
    expect(firstItemType).toBe('CREATED');

    // Check modification items
    const modificationItems = page.locator('[data-testid="history-item"]', {
      has: page.locator('[data-testid="change-type"]', { hasText: 'MODIFICATION' })
    });
    await expect(modificationItems).toHaveCount(3);

    // Verify each modification shows approver
    const firstModApprover = await page.textContent(
      '[data-testid="history-item"]:nth-child(2) [data-testid="change-approver"]'
    );
    expect(firstModApprover).toContain('test-reviewer');

    // Verify timestamps are shown
    const firstModTime = await page.textContent(
      '[data-testid="history-item"]:nth-child(2) [data-testid="change-timestamp"]'
    );
    expect(firstModTime).toMatch(/\d{1,2}\/\d{1,2}\/\d{4}/); // Date format
  });

  test('P1-044: Pending changes block publish action', async ({ page }) => {
    // Create campaign with pending change
    campaignId = await createCampaignWithPendingChange(page, {
      name: 'Block Publish Test',
      status: 'APPROVED',
      modifications: { description: 'Pending change' }
    });

    // Navigate to campaign
    await page.goto(`/${workspaceId}/campaigns/${campaignId}`);
    await page.waitForSelector('[data-testid="campaign-detail-page"]');

    // Try to click publish
    await page.click('[data-testid="publish-campaign-button"]');

    // Verify warning modal appears
    await page.waitForSelector('[data-testid="pending-changes-warning-modal"]');
    const warningText = await page.textContent('[data-testid="pending-changes-warning-modal"]');
    expect(warningText).toContain('pending changes');
    expect(warningText).toContain('cannot publish');

    // Verify cancel/resolve options
    await expect(page.locator('[data-testid="resolve-changes-button"]')).toBeVisible();
    await expect(page.locator('[data-testid="cancel-publish-button"]')).toBeVisible();

    // Clicking publish anyway should fail
    await page.click('[data-testid="force-publish-button"]');
    await page.waitForSelector('[data-testid="publish-error-message"]');
    const errorText = await page.textContent('[data-testid="publish-error-message"]');
    expect(errorText).toContain('pending changes must be resolved');
  });

  test('P1-044: Modification reason is required', async ({ page }) => {
    campaignId = await createCampaign(page, { name: 'Reason Test Campaign' });

    await page.goto(`/${workspaceId}/campaigns/${campaignId}`);
    await page.click('[data-testid="edit-campaign-button"]');

    // Modify without reason
    await page.fill('[data-testid="campaign-name-input"]', 'New Name');

    // Try to save without reason
    await page.click('[data-testid="save-campaign-button"]');

    // Verify validation error
    await page.waitForSelector('[data-testid="validation-error-message"]');
    const errorText = await page.textContent('[data-testid="validation-error-message"]');
    expect(errorText).toContain('modification reason');

    // Add reason and save
    await page.fill(
      '[data-testid="modification-reason-input"]',
      'Updated for testing'
    );
    await page.click('[data-testid="save-campaign-button"]');

    // Verify success
    await page.waitForSelector('[data-testid="modification-pending-banner"]');
  });

  // Helper functions

  async function createCampaign(page: Page, data: {
    name: string;
    description?: string;
    status?: string;
    budget?: { totalBudget: number; currency: string };
    hasApprovedStrategy?: boolean;
  }): Promise<string> {
    await page.goto(`/${workspaceId}/campaigns/new`);
    await page.waitForSelector('[data-testid="campaign-create-form"]');

    await page.fill('[data-testid="campaign-name-input"]', data.name);
    
    if (data.description) {
      await page.fill('[data-testid="campaign-description-input"]', data.description);
    }

    await page.click('[data-testid="create-campaign-button"]');
    await page.waitForURL(/\/campaigns\/[\w-]+$/);

    const url = page.url();
    const match = url.match(/\/campaigns\/([\w-]+)$/);
    return match ? match[1] : '';
  }

  async function createCampaignWithPendingChange(page: Page, data: {
    name: string;
    originalName?: string;
    modifications: Record<string, string>;
    status?: string;
  }): Promise<string> {
    const id = await createCampaign(page, {
      name: data.originalName || data.name,
      status: data.status
    });

    await page.goto(`/${workspaceId}/campaigns/${id}`);
    await page.click('[data-testid="edit-campaign-button"]');

    if (data.modifications.name) {
      await page.fill('[data-testid="campaign-name-input"]', data.modifications.name);
    }
    if (data.modifications.description) {
      await page.fill('[data-testid="campaign-description-input"]', data.modifications.description);
    }

    await page.fill('[data-testid="modification-reason-input"]', 'Testing modification workflow');
    await page.click('[data-testid="save-campaign-button"]');
    await page.waitForSelector('[data-testid="modification-pending-banner"]');

    return id;
  }

  async function getPendingApprovalId(page: Page, campaignId: string): Promise<string> {
    await page.goto(`/${workspaceId}/approvals`);
    await page.waitForSelector('[data-testid="approvals-list-page"]');

    const approvalLink = page.locator(`[data-testid="approval-link"][data-campaign-id="${campaignId}"]`);
    await approvalLink.click();

    await page.waitForURL(/\/approvals\/[\w-]+$/);
    const url = page.url();
    const match = url.match(/\/approvals\/([\w-]+)$/);
    return match ? match[1] : '';
  }

  async function createCampaignWithChangeHistory(page: Page, data: {
    name: string;
    changes: Array<{ field: string; from: string; to: string }>;
  }): Promise<string> {
    const id = await createCampaign(page, { name: data.name });

    for (const change of data.changes) {
      // Create and approve each change
      await createCampaignWithPendingChange(page, {
        name: data.name,
        modifications: { [change.field]: change.to }
      });

      // Approve the change
      const approvalId = await getPendingApprovalId(page, id);
      await page.goto(`/${workspaceId}/approvals/${approvalId}`);
      await page.fill('[data-testid="approval-comment-input"]', 'Approved');
      await page.click('[data-testid="approve-button"]');
      await page.waitForSelector('[data-testid="approval-success-message"]');
    }

    return id;
  }
});
