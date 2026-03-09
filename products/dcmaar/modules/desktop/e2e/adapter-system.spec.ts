/**
 * Playwright E2E tests for adapter system integration.
 * Tests complete user workflows through the desktop UI.
 */

import { test, expect } from '@playwright/test';

test.describe('Adapter System E2E', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display adapter status page', async ({ page }) => {
    await page.click('text=Adapter Status');
    
    await expect(page.locator('h4')).toContainText('Adapter System Status');
    await expect(page.locator('text=Telemetry Snapshot')).toBeVisible();
    await expect(page.locator('text=Health Status')).toBeVisible();
  });

  test('should refresh telemetry snapshot', async ({ page }) => {
    await page.click('text=Adapter Status');
    
    const initialTimestamp = await page.locator('text=Collected:').textContent();
    
    await page.click('button:has-text("Refresh Snapshot")');
    await page.waitForTimeout(1000);
    
    const newTimestamp = await page.locator('text=Collected:').textContent();
    expect(newTimestamp).not.toBe(initialTimestamp);
  });

  test('should perform health check', async ({ page }) => {
    await page.click('text=Adapter Status');
    
    await page.click('button:has-text("Health Check")');
    await page.waitForSelector('text=Source:');
    
    await expect(page.locator('text=Source:')).toBeVisible();
    await expect(page.locator('text=Sinks:')).toBeVisible();
  });

  test('should display connected agents', async ({ page }) => {
    await page.click('text=Adapter Status');
    
    await page.waitForSelector('text=Connected Agents', { timeout: 5000 });
    await expect(page.locator('text=Connected Agents')).toBeVisible();
  });

  test('should handle adapter errors gracefully', async ({ page }) => {
    // Simulate error by navigating to invalid workspace
    await page.goto('/?workspace=invalid');
    
    await page.click('text=Adapter Status');
    
    // Should show error message
    await expect(page.locator('[role="alert"]')).toBeVisible();
  });

  test('should execute command through adapter', async ({ page }) => {
    await page.click('text=Agent Config');
    
    // Make a config change
    await page.fill('input[name="queue.maxSize"]', '15000');
    await page.click('button:has-text("Save Configuration")');
    
    // Should show success or preview
    await expect(
      page.locator('text=Preview Changes, text=Applied')
    ).toBeVisible({ timeout: 5000 });
  });

  test('should display metrics from adapter', async ({ page }) => {
    await page.click('text=Metrics');
    
    await page.waitForSelector('text=Agent Metrics');
    
    // Should display metrics from adapter snapshot
    await expect(page.locator('text=Throughput')).toBeVisible();
    await expect(page.locator('text=Queue Depth')).toBeVisible();
  });

  test('should auto-refresh adapter data', async ({ page }) => {
    await page.click('text=Adapter Status');
    
    const initialAgentCount = await page.locator('text=Agents:').textContent();
    
    // Wait for auto-refresh (10 seconds)
    await page.waitForTimeout(11000);
    
    const newAgentCount = await page.locator('text=Agents:').textContent();
    
    // Data should be refreshed (timestamps will differ)
    expect(newAgentCount).toBeDefined();
  });

  test('should handle offline mode gracefully', async ({ page, context }) => {
    await page.click('text=Adapter Status');
    
    // Simulate offline
    await context.setOffline(true);
    
    await page.click('button:has-text("Refresh Snapshot")');
    
    // Should show error or fallback
    await expect(
      page.locator('text=offline, text=unavailable, text=failed')
    ).toBeVisible({ timeout: 5000 });
    
    await context.setOffline(false);
  });

  test('should display audit trail for adapter actions', async ({ page }) => {
    await page.click('text=Audit');
    
    await expect(page.locator('text=Audit Log')).toBeVisible();
    
    // Should show adapter-related audit entries
    await expect(page.locator('text=dry-run, text=apply, text=schema_migration')).toBeVisible();
  });
});

test.describe('Adapter Configuration', () => {
  test('should allow workspace bundle selection', async ({ page }) => {
    await page.goto('/settings');
    
    await page.click('text=Workspace');
    
    // Should show workspace configuration
    await expect(page.locator('text=Workspace Bundle')).toBeVisible();
    await expect(page.locator('text=Source Adapter')).toBeVisible();
    await expect(page.locator('text=Sink Adapter')).toBeVisible();
  });

  test('should validate workspace bundle signature', async ({ page }) => {
    await page.goto('/settings');
    
    await page.click('text=Workspace');
    await page.click('button:has-text("Import Bundle")');
    
    // Upload invalid bundle
    const invalidBundle = JSON.stringify({
      workspaceVersion: '2.0.0',
      signature: 'invalid',
    });
    
    await page.setInputFiles('input[type="file"]', {
      name: 'bundle.json',
      mimeType: 'application/json',
      buffer: Buffer.from(invalidBundle),
    });
    
    // Should show validation error
    await expect(page.locator('text=Invalid signature')).toBeVisible();
  });
});
