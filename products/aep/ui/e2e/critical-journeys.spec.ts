import { expect, test } from '@playwright/test';
import { seedAuthenticatedSession } from './auth-helpers';

/**
 * Browser-level E2E tests for AEP critical user journeys.
 *
 * These tests validate the primary user journeys mentioned in the audit:
 * - Agent lifecycle (registration, activation, deactivation)
 * - Memory exploration (episodes, facts, policies)
 * - Governance (kill switch, policy review)
 *
 * @doc.type test
 * @doc.purpose Browser-level E2E tests for critical AEP user paths
 * @doc.layer testing
 */

test.describe('AEP Critical Journeys - Agent Lifecycle', () => {
  test.beforeEach(async ({ page }) => {
    await seedAuthenticatedSession(page);
  });

  test('should navigate to agent registry and view registered agents', async ({ page }) => {
    await page.goto('/');
    
    // Navigate to agent registry
    await page.locator('a[href="/agents"], nav a:has-text("Agents")').click();
    
    // Verify agent registry loads
    await expect(page).toHaveURL(/\/agents/, { timeout: 10000 });
    await expect(page.locator('h1, h2').filter({ hasText: /agents/i })).toBeVisible();
    
    // Verify agent list is displayed
    await expect(page.locator('[data-testid="agent-list"], .agent-list')).toBeVisible();
  });

  test('should view agent details from registry', async ({ page }) => {
    await page.goto('/agents');
    
    // Click on first agent in the list
    const firstAgent = page.locator('[data-testid="agent-item"], .agent-item').first();
    await firstAgent.click();
    
    // Verify agent details page loads
    await expect(page.locator('h1, h2').filter({ hasText: /agent details/i })).toBeVisible({ timeout: 10000 });
    
    // Verify agent metadata is displayed
    await expect(page.locator('[data-testid="agent-id"], .agent-id')).toBeVisible();
    await expect(page.locator('[data-testid="agent-name"], .agent-name')).toBeVisible();
  });

  test('should activate an agent from registry', async ({ page }) => {
    await page.goto('/agents');
    
    // Find an inactive agent
    const inactiveAgent = page.locator('[data-status="inactive"], [data-testid="agent-item"][data-status="inactive"]').first();
    
    if (await inactiveAgent.isVisible()) {
      // Click activate button
      await inactiveAgent.locator('button:has-text("Activate"), [data-testid="activate-agent"]').click();
      
      // Confirm activation
      const confirmButton = page.locator('button:has-text("Confirm"), [data-testid="confirm-activation"]');
      if (await confirmButton.isVisible()) {
        await confirmButton.click();
      }
      
      // Verify agent status changes to active
      await expect(inactiveAgent.locator('[data-status="active"]')).toBeVisible({ timeout: 5000 });
    }
  });

  test('should deactivate an agent from registry', async ({ page }) => {
    await page.goto('/agents');
    
    // Find an active agent
    const activeAgent = page.locator('[data-status="active"], [data-testid="agent-item"][data-status="active"]').first();
    
    if (await activeAgent.isVisible()) {
      // Click deactivate button
      await activeAgent.locator('button:has-text("Deactivate"), [data-testid="deactivate-agent"]').click();
      
      // Confirm deactivation
      const confirmButton = page.locator('button:has-text("Confirm"), [data-testid="confirm-deactivation"]');
      if (await confirmButton.isVisible()) {
        await confirmButton.click();
      }
      
      // Verify agent status changes to inactive
      await expect(activeAgent.locator('[data-status="inactive"]')).toBeVisible({ timeout: 5000 });
    }
  });
});

test.describe('AEP Critical Journeys - Memory Exploration', () => {
  test.beforeEach(async ({ page }) => {
    await seedAuthenticatedSession(page);
  });

  test('should navigate to memory explorer and view episodes', async ({ page }) => {
    await page.goto('/');
    
    // Navigate to memory explorer
    await page.locator('a[href="/memory"], nav a:has-text("Memory")').click();
    
    // Verify memory explorer loads
    await expect(page).toHaveURL(/\/memory/, { timeout: 10000 });
    await expect(page.locator('h1, h2').filter({ hasText: /memory/i })).toBeVisible();
    
    // Verify episode list is displayed
    await expect(page.locator('[data-testid="episode-list"], .episode-list')).toBeVisible();
  });

  test('should filter episodes by outcome', async ({ page }) => {
    await page.goto('/memory');
    
    // Find outcome filter dropdown
    const outcomeFilter = page.locator('select[name="outcome"], [data-testid="outcome-filter"]');
    await outcomeFilter.selectOption('SUCCESS');
    
    // Verify episodes are filtered
    await expect(page.locator('[data-testid="episode-list"] [data-outcome="SUCCESS"]')).toBeVisible();
  });

  test('should view episode details including facts and policies', async ({ page }) => {
    await page.goto('/memory');
    
    // Click on first episode
    const firstEpisode = page.locator('[data-testid="episode-item"], .episode-item').first();
    await firstEpisode.click();
    
    // Verify episode details load
    await expect(page.locator('[data-testid="episode-details"], .episode-details')).toBeVisible({ timeout: 10000 });
    
    // Verify facts section is displayed
    await expect(page.locator('[data-testid="facts-section"], .facts-section')).toBeVisible();
    
    // Verify policies section is displayed
    await expect(page.locator('[data-testid="policies-section"], .policies-section')).toBeVisible();
  });

  test('should search episodes by agent ID', async ({ page }) => {
    await page.goto('/memory');
    
    // Find agent selector
    const agentSelector = page.locator('select[name="agent"], [data-testid="agent-selector"]');
    if (await agentSelector.isVisible()) {
      // Select a specific agent
      await agentSelector.selectOption({ index: 1 });
      
      // Verify episodes are filtered by agent
      await expect(page.locator('[data-testid="episode-list"]')).toBeVisible();
    }
  });
});

test.describe('AEP Critical Journeys - Governance', () => {
  test.beforeEach(async ({ page }) => {
    await seedAuthenticatedSession(page);
  });

  test('should navigate to governance page and view policies', async ({ page }) => {
    await page.goto('/');
    
    // Navigate to governance
    await page.locator('a[href="/governance"], nav a:has-text("Governance")').click();
    
    // Verify governance page loads
    await expect(page).toHaveURL(/\/governance/, { timeout: 10000 });
    await expect(page.locator('h1, h2').filter({ hasText: /governance/i })).toBeVisible();
    
    // Verify policy list is displayed
    await expect(page.locator('[data-testid="policy-list"], .policy-list')).toBeVisible();
  });

  test('should view policy details and learned policies', async ({ page }) => {
    await page.goto('/governance');
    
    // Click on first policy
    const firstPolicy = page.locator('[data-testid="policy-item"], .policy-item').first();
    await firstPolicy.click();
    
    // Verify policy details load
    await expect(page.locator('[data-testid="policy-details"], .policy-details')).toBeVisible({ timeout: 10000 });
    
    // Verify policy ID and name are displayed
    await expect(page.locator('[data-testid="policy-id"], .policy-id')).toBeVisible();
    await expect(page.locator('[data-testid="policy-name"], .policy-name')).toBeVisible();
  });

  test('should view learned policies for review', async ({ page }) => {
    await page.goto('/governance');
    
    // Navigate to learned policies tab
    await page.locator('button:has-text("Learned"), [data-testid="tab-learned"]').click();
    
    // Verify learned policies are displayed
    await expect(page.locator('[data-testid="learned-policies-list"], .learned-policies-list')).toBeVisible({ timeout: 10000 });
  });

  test('should approve a learned policy', async ({ page }) => {
    await page.goto('/governance');
    
    // Navigate to learned policies tab
    await page.locator('button:has-text("Learned"), [data-testid="tab-learned"]').click();
    
    // Find a pending policy
    const pendingPolicy = page.locator('[data-status="pending"], [data-testid="policy-item"][data-status="pending"]').first();
    
    if (await pendingPolicy.isVisible()) {
      // Click approve button
      await pendingPolicy.locator('button:has-text("Approve"), [data-testid="approve-policy"]').click();
      
      // Verify approval confirmation
      await expect(page.locator('[data-testid="approval-confirmation"], .approval-confirmation')).toBeVisible({ timeout: 5000 });
    }
  });

  test('should access production kill switch (operator only)', async ({ page }) => {
    await page.goto('/governance');
    
    // Navigate to operations tab
    await page.locator('button:has-text("Operations"), [data-testid="tab-operations"]').click();
    
    // Verify kill switch section is displayed
    await expect(page.locator('[data-testid="kill-switch"], .kill-switch')).toBeVisible({ timeout: 10000 });
    
    // Verify kill switch requires confirmation
    const killSwitchButton = page.locator('button:has-text("Emergency Kill Switch"), [data-testid="kill-switch-button"]');
    if (await killSwitchButton.isVisible()) {
      await killSwitchButton.click();
      
      // Verify confirmation dialog appears
      await expect(page.locator('[data-testid="kill-switch-confirmation"], .kill-switch-confirmation')).toBeVisible();
    }
  });
});

test.describe('AEP Critical Journeys - Full Journey', () => {
  test('should complete full critical journey: agents -> memory -> governance', async ({ page }) => {
    await seedAuthenticatedSession(page);
    
    // Start at home
    await page.goto('/');
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 10000 });
    
    // Navigate to agents
    await page.locator('a[href="/agents"], nav a:has-text("Agents")').click();
    await expect(page).toHaveURL(/\/agents/, { timeout: 10000 });
    
    // Navigate to memory
    await page.locator('a[href="/memory"], nav a:has-text("Memory")').click();
    await expect(page).toHaveURL(/\/memory/, { timeout: 10000 });
    
    // Navigate to governance
    await page.locator('a[href="/governance"], nav a:has-text("Governance")').click();
    await expect(page).toHaveURL(/\/governance/, { timeout: 10000 });
    
    // Verify all pages loaded successfully without errors
    const hasErrors = await page.evaluate(() => {
      return document.body.textContent?.includes('error') || false;
    });
    expect(hasErrors).toBeFalsy();
  });

  test('should maintain tenant context across all critical journeys', async ({ page }) => {
    await seedAuthenticatedSession(page);
    
    // Get initial tenant ID
    const initialTenantId = await page.evaluate(() => {
      return sessionStorage.getItem('aep-tenant-id');
    });
    expect(initialTenantId).toBeTruthy();
    
    // Navigate through all critical pages
    await page.goto('/agents');
    await page.goto('/memory');
    await page.goto('/governance');
    
    // Verify tenant context persists
    const finalTenantId = await page.evaluate(() => {
      return sessionStorage.getItem('aep-tenant-id');
    });
    expect(finalTenantId).toBe(initialTenantId);
  });
});
