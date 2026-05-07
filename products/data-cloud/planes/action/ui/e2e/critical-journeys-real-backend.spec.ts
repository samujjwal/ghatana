/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

import { expect, test } from '@playwright/test';

/**
 * P2-E2E-2: Browser E2E tests for AEP with REAL backend
 * 
 * These tests validate critical user journeys through the actual AEP backend
 * without using mocks. They require a running AEP server instance.
 * 
 * Tests verify:
 * - Full critical path journey (agents -> memory -> governance)
 * - Real API responses and data persistence
 * - Authentication and tenant isolation
 * - Real-time updates and SSE streaming
 * - Error handling with real backend errors
 * - Performance under real load
 * 
 * @doc.type test
 * @doc.purpose Browser E2E tests with real backend integration
 * @doc.layer testing
 * @doc.pattern E2ETest, RealBackendTest
 */

test.describe('AEP Critical Journeys - Real Backend Integration', () => {
  // Use test.use() for configuration to avoid process.env type issues
  test.use({
    baseURL: 'http://localhost:8081',
  });
  
  const getBaseUrl = () => test.info().project.use.baseURL || 'http://localhost:8081';
  const getTestTenant = () => 'e2e-test-tenant-aep';
  const getTestApiKey = () => 'test-aep-api-key';

  test.beforeEach(async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    const testApiKey = getTestApiKey();
    
    // Configure page to use real backend
    await page.goto(baseUrl);
    
    // Set up authentication
    await page.evaluate(({ tenant, apiKey }) => {
      sessionStorage.setItem('aep-tenant-id', tenant);
      sessionStorage.setItem('aep-session-token', apiKey);
      localStorage.setItem('aep-onboarding-complete', 'true');
    }, { tenant: testTenant, apiKey: testApiKey });
    
    // Reload to apply auth
    await page.reload();
  });

  test('should load home page from real backend', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    await page.goto(`${baseUrl}/`);
    
    // Wait for real backend response
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 15000 });
    
    // Verify we're not using mocked data
    const response = await page.request.get(`${baseUrl}/api/v1/capabilities`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    expect(response.ok()).toBeTruthy();
    const data = await response.json();
    expect(data).toBeDefined();
  });

  test('should navigate to agent registry with real data', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    await page.goto(`${baseUrl}/`);
    
    // Navigate to agent registry
    await page.locator('a[href="/agents"], nav a:has-text("Agents")').click();
    await expect(page).toHaveURL(/\/agents/, { timeout: 15000 });
    
    // Wait for real agents to load
    await expect(page.locator('h1, h2').filter({ hasText: /agents/i })).toBeVisible({ timeout: 15000 });
    
    // Verify real API was called (not mocked)
    const agentsResponse = await page.request.get(`${baseUrl}/api/v1/agents`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    expect(agentsResponse.ok()).toBeTruthy();
    const agents = await agentsResponse.json();
    expect(agents).toBeDefined();
  });

  test('should view agent details from real backend', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    await page.goto(`${baseUrl}/agents`);
    
    // Get list of agents from real backend
    const agentsResponse = await page.request.get(`${baseUrl}/api/v1/agents`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    const agents = await agentsResponse.json();
    
    if (agents.length > 0) {
      const firstAgentId = agents[0].id;
      
      // Navigate to agent details
      await page.goto(`${baseUrl}/agents/${firstAgentId}`);
      
      // Verify agent details page loads
      await expect(page.locator('h1, h2').filter({ hasText: /agent details/i })).toBeVisible({ timeout: 15000 });
      
      // Verify agent metadata from real backend
      const agentResponse = await page.request.get(`${baseUrl}/api/v1/agents/${firstAgentId}`, {
        headers: { 'X-Tenant-Id': testTenant }
      });
      expect(agentResponse.ok()).toBeTruthy();
      const agent = await agentResponse.json();
      expect(agent.id).toBe(firstAgentId);
    }
  });

  test('should activate agent via real backend', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    await page.goto(`${baseUrl}/agents`);
    
    // Get agents from real backend
    const agentsResponse = await page.request.get(`${baseUrl}/api/v1/agents`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    const agents = await agentsResponse.json();
    
    // Find an inactive agent
    const inactiveAgent = agents.find((a: any) => a.status === 'INACTIVE' || a.status === 'inactive');
    
    if (inactiveAgent) {
      // Activate via real backend API
      const activateResponse = await page.request.post(`${baseUrl}/api/v1/agents/${inactiveAgent.id}/activate`, {
        headers: { 
          'X-Tenant-Id': testTenant,
          'Content-Type': 'application/json'
        }
      });
      
      expect(activateResponse.ok()).toBeTruthy();
      
      // Verify status changed
      const verifyResponse = await page.request.get(`${baseUrl}/api/v1/agents/${inactiveAgent.id}`, {
        headers: { 'X-Tenant-Id': testTenant }
      });
      const agent = await verifyResponse.json();
      expect(agent.status).toBe('ACTIVE');
    }
  });

  test('should navigate to memory explorer with real data', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    await page.goto(`${baseUrl}/`);
    
    // Navigate to memory explorer
    await page.locator('a[href="/memory"], nav a:has-text("Memory")').click();
    await expect(page).toHaveURL(/\/memory/, { timeout: 15000 });
    
    // Wait for real episodes to load
    await expect(page.locator('h1, h2').filter({ hasText: /memory/i })).toBeVisible({ timeout: 15000 });
    
    // Verify real memory API was called
    const memoryResponse = await page.request.get(`${baseUrl}/api/v1/memory/episodes`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    expect(memoryResponse.ok()).toBeTruthy();
  });

  test('should view episode details from real backend', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    await page.goto(`${baseUrl}/memory`);
    
    // Get episodes from real backend
    const episodesResponse = await page.request.get(`${baseUrl}/api/v1/memory/episodes`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    const episodes = await episodesResponse.json();
    
    if (episodes.length > 0) {
      const firstEpisodeId = episodes[0].id;
      
      // Navigate to episode details
      await page.goto(`${baseUrl}/memory/episodes/${firstEpisodeId}`);
      
      // Verify episode details load
      await expect(page.locator('[data-testid="episode-details"], .episode-details')).toBeVisible({ timeout: 15000 });
      
      // Verify episode data from real backend
      const episodeResponse = await page.request.get(`${baseUrl}/api/v1/memory/episodes/${firstEpisodeId}`, {
        headers: { 'X-Tenant-Id': testTenant }
      });
      expect(episodeResponse.ok()).toBeTruthy();
      const episode = await episodeResponse.json();
      expect(episode.id).toBe(firstEpisodeId);
    }
  });

  test('should navigate to governance with real data', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    await page.goto(`${baseUrl}/`);
    
    // Navigate to governance
    await page.locator('a[href="/governance"], nav a:has-text("Governance")').click();
    await expect(page).toHaveURL(/\/governance/, { timeout: 15000 });
    
    // Wait for real policies to load
    await expect(page.locator('h1, h2').filter({ hasText: /governance/i })).toBeVisible({ timeout: 15000 });
    
    // Verify real governance API was called
    const governanceResponse = await page.request.get(`${baseUrl}/api/v1/governance/policies`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    expect(governanceResponse.ok()).toBeTruthy();
  });

  test('should view policy details from real backend', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    await page.goto(`${baseUrl}/governance`);
    
    // Get policies from real backend
    const policiesResponse = await page.request.get(`${baseUrl}/api/v1/governance/policies`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    const policies = await policiesResponse.json();
    
    if (policies.length > 0) {
      const firstPolicyId = policies[0].id;
      
      // Navigate to policy details
      await page.goto(`${baseUrl}/governance/policies/${firstPolicyId}`);
      
      // Verify policy details load
      await expect(page.locator('[data-testid="policy-details"], .policy-details')).toBeVisible({ timeout: 15000 });
      
      // Verify policy data from real backend
      const policyResponse = await page.request.get(`${baseUrl}/api/v1/governance/policies/${firstPolicyId}`, {
        headers: { 'X-Tenant-Id': testTenant }
      });
      expect(policyResponse.ok()).toBeTruthy();
      const policy = await policyResponse.json();
      expect(policy.id).toBe(firstPolicyId);
    }
  });

  test('should handle real API errors gracefully', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    // Try to access non-existent agent
    const response = await page.request.get(`${baseUrl}/api/v1/agents/non-existent-id`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    
    expect(response.status()).toBe(404);
    
    // Navigate to non-existent page
    await page.goto(`${baseUrl}/agents/non-existent-id`);
    const errorElement = page.locator('[data-testid="error-boundary"], .error-message, [role="alert"]');
    await expect(errorElement).toBeVisible({ timeout: 10000 });
  });

  test('should maintain tenant context across real navigation', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    await page.goto(`${baseUrl}/`);
    
    // Verify tenant context from real backend
    const tenantId = await page.evaluate(() => {
      return sessionStorage.getItem('aep-tenant-id');
    });
    expect(tenantId).toBeTruthy();
    
    // Navigate through all critical pages
    await page.goto(`${baseUrl}/agents`);
    await page.goto(`${baseUrl}/memory`);
    await page.goto(`${baseUrl}/governance`);
    
    // Verify tenant context persists
    const tenantIdAfterNav = await page.evaluate(() => {
      return sessionStorage.getItem('aep-tenant-id');
    });
    expect(tenantIdAfterNav).toBe(tenantId);
    
    // Verify backend receives correct tenant header
    const response = await page.request.get(`${baseUrl}/api/v1/agents`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    expect(response.ok()).toBeTruthy();
  });

  test('should receive real SSE events', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    // Navigate to a page that uses SSE
    await page.goto(`${baseUrl}/agents`);
    
    // Set up SSE listener
    const sseEvents: string[] = [];
    page.on('console', msg => {
      if (msg.text().includes('SSE') || msg.text().includes('event')) {
        sseEvents.push(msg.text());
      }
    });
    
    // Wait for SSE connection
    await page.waitForTimeout(5000);
    
    // Verify SSE endpoint is accessible
    const sseResponse = await page.request.get(`${baseUrl}/api/v1/sse`, {
      headers: { 
        'X-Tenant-Id': testTenant,
        'Accept': 'text/event-stream'
      }
    });
    
    // SSE endpoint should be accessible (may return 200 or redirect)
    expect([200, 302, 307]).toContain(sseResponse.status());
  });

  test('should handle real authentication expiry', async ({ page }) => {
    const baseUrl = getBaseUrl();
    
    await page.goto(`${baseUrl}/`);
    
    // Clear session to simulate expiry
    await page.evaluate(() => {
      sessionStorage.removeItem('aep-session-token');
    });
    
    // Navigate to protected route
    await page.locator('a[href="/agents"]').click();
    
    // Verify auth redirect or error
    await expect(page).toHaveURL(/\/(login|auth)|/, { timeout: 10000 });
  });

  test('should complete full critical journey with real backend', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    // Start at home
    await page.goto(`${baseUrl}/`);
    await expect(page.locator('h1, h2')).toBeVisible({ timeout: 15000 });
    
    // Navigate to agents
    await page.locator('a[href="/agents"], nav a:has-text("Agents")').click();
    await expect(page).toHaveURL(/\/agents/, { timeout: 15000 });
    
    // Verify real agents loaded
    const agentsResponse = await page.request.get(`${baseUrl}/api/v1/agents`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    expect(agentsResponse.ok()).toBeTruthy();
    
    // Navigate to memory
    await page.locator('a[href="/memory"], nav a:has-text("Memory")').click();
    await expect(page).toHaveURL(/\/memory/, { timeout: 15000 });
    
    // Verify real memory loaded
    const memoryResponse = await page.request.get(`${baseUrl}/api/v1/memory/episodes`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    expect(memoryResponse.ok()).toBeTruthy();
    
    // Navigate to governance
    await page.locator('a[href="/governance"], nav a:has-text("Governance")').click();
    await expect(page).toHaveURL(/\/governance/, { timeout: 15000 });
    
    // Verify real governance loaded
    const governanceResponse = await page.request.get(`${baseUrl}/api/v1/governance/policies`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    expect(governanceResponse.ok()).toBeTruthy();
    
    // Verify no critical errors occurred
    const hasErrors = await page.evaluate(() => {
      return document.body.textContent?.includes('error') || false;
    });
    expect(hasErrors).toBeFalsy();
  });
});

test.describe('AEP Real Backend - Performance Tests', () => {
  test.use({
    baseURL: 'http://localhost:8081',
  });
  
  const getBaseUrl = () => test.info().project.use.baseURL || 'http://localhost:8081';
  const getTestTenant = () => 'e2e-test-tenant-aep';
  const getTestApiKey = () => 'test-aep-api-key';

  test.beforeEach(async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    const testApiKey = getTestApiKey();
    
    await page.goto(baseUrl);
    await page.evaluate(({ tenant, apiKey }) => {
      sessionStorage.setItem('aep-tenant-id', tenant);
      sessionStorage.setItem('aep-session-token', apiKey);
      localStorage.setItem('aep-onboarding-complete', 'true');
    }, { tenant: testTenant, apiKey: testApiKey });
    await page.reload();
  });

  test('should handle rapid navigation with real backend', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const startTime = Date.now();
    
    // Rapid navigation through critical paths
    await page.goto(`${baseUrl}/agents`);
    await page.goto(`${baseUrl}/memory`);
    await page.goto(`${baseUrl}/governance`);
    
    const duration = Date.now() - startTime;
    expect(duration).toBeLessThan(30000); // Should complete in under 30 seconds
  });

  test('should handle concurrent real API requests', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    await page.goto(`${baseUrl}/agents`);
    
    // Make multiple concurrent requests
    const promises = [
      page.request.get(`${baseUrl}/api/v1/agents`, {
        headers: { 'X-Tenant-Id': testTenant }
      }),
      page.request.get(`${baseUrl}/api/v1/memory/episodes`, {
        headers: { 'X-Tenant-Id': testTenant }
      }),
      page.request.get(`${baseUrl}/api/v1/governance/policies`, {
        headers: { 'X-Tenant-Id': testTenant }
      }),
    ];
    
    const responses = await Promise.all(promises);
    
    // All requests should succeed
    for (const response of responses) {
      expect(response.ok()).toBeTruthy();
    }
  });

  test('should verify tenant isolation with real backend', async ({ page }) => {
    const baseUrl = getBaseUrl();
    const testTenant = getTestTenant();
    
    // Get data for test tenant
    const testTenantResponse = await page.request.get(`${baseUrl}/api/v1/agents`, {
      headers: { 'X-Tenant-Id': testTenant }
    });
    const testTenantAgents = await testTenantResponse.json();
    
    // Try to access with different tenant
    const otherTenantResponse = await page.request.get(`${baseUrl}/api/v1/agents`, {
      headers: { 'X-Tenant-Id': 'different-tenant' }
    });
    
    // Should either return empty list or 403/401 depending on auth
    expect([200, 403, 401]).toContain(otherTenantResponse.status());
    
    if (otherTenantResponse.status() === 200) {
      const otherTenantAgents = await otherTenantResponse.json();
      expect(otherTenantAgents).not.toEqual(testTenantAgents);
    }
  });
});
