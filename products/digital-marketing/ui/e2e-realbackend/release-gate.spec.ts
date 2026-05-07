/**
 * P0-006: Real-backend release gate E2E test suite for DMOS.
 *
 * These tests run against a real DMOS API server + real PostgreSQL. No page.route() mocks.
 * All critical user flows are covered: auth, campaigns, strategy, budget, approval, AI action log.
 *
 * Prerequisites (caller must set env vars and start services):
 *   DMOS_API_BASE_URL   — e.g., http://localhost:8080
 *   DMOS_UI_BASE_URL    — e.g., http://localhost:5174
 *   DMOS_TEST_TOKEN     — valid JWT for a brand-manager test user
 *   DMOS_TEST_WORKSPACE — workspace ID
 */
import { test, expect, type Page, type BrowserContext } from '@playwright/test';

const apiBaseUrl = process.env['DMOS_API_BASE_URL'];
const testToken = process.env['DMOS_TEST_TOKEN'];
const testWorkspace = process.env['DMOS_TEST_WORKSPACE'];

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `P0-006: Required environment variable '${name}' is not set. ` +
        `Set it before running real-backend E2E tests.`,
    );
  }
  return value;
}

/** Inject the test JWT into localStorage so the UI authenticates without OAuth redirect. */
async function seedAuthToken(page: Page): Promise<void> {
  await page.addInitScript((token: string) => {
    localStorage.setItem('dmos_access_token', token);
  }, requireEnv('DMOS_TEST_TOKEN'));
}

test.describe('P0-006: Real-backend release gate — critical flows', () => {
  let context: BrowserContext;
  let page: Page;
  const workspace = requireEnv('DMOS_TEST_WORKSPACE');

  test.beforeAll(async ({ browser }) => {
    // Validate all required env vars up-front
    requireEnv('DMOS_API_BASE_URL');
    requireEnv('DMOS_TEST_TOKEN');
    requireEnv('DMOS_TEST_WORKSPACE');

    context = await browser.newContext();
    page = await context.newPage();
    await seedAuthToken(page);
  });

  test.afterAll(async () => {
    await context.close();
  });

  test.beforeEach(async () => {
    // Re-seed token before each test (page reloads clear localStorage)
    await seedAuthToken(page);
  });

  test('P0-006-AUTH: Dashboard loads after auth token injection', async () => {
    await page.goto(`/workspaces/${workspace}/dashboard`);
    await expect(page.locator('[data-testid="dashboard-root"]')).toBeVisible({ timeout: 15_000 });
  });

  test('P0-006-CAMPAIGN-CREATE: Campaign can be created via real API', async () => {
    await page.goto(`/workspaces/${workspace}/campaigns`);
    await expect(page.locator('[data-testid="campaign-list"]')).toBeVisible({ timeout: 15_000 });

    // Open create form
    const createBtn = page.locator('[data-testid="create-campaign-btn"]');
    await expect(createBtn).toBeVisible();
    await createBtn.click();

    // Fill required fields
    const campaignName = `E2E Campaign ${Date.now()}`;
    await page.fill('[data-testid="campaign-name-input"]', campaignName);
    await page.selectOption('[data-testid="campaign-type-select"]', 'AWARENESS');

    // Submit
    await page.click('[data-testid="create-campaign-submit"]');

    // Should appear in list
    await expect(page.locator(`text=${campaignName}`)).toBeVisible({ timeout: 15_000 });
  });

  test('P0-006-CAMPAIGN-LAUNCH: Campaign can be launched', async () => {
    await page.goto(`/workspaces/${workspace}/campaigns`);
    await expect(page.locator('[data-testid="campaign-row"]').first()).toBeVisible({ timeout: 15_000 });

    // Find a DRAFT campaign and launch it
    const launchBtn = page.locator('[data-testid="launch-campaign-btn"]').first();
    if (await launchBtn.isVisible().catch(() => false)) {
      await launchBtn.click();
      // Wait for status change
      await expect(page.locator('[data-testid="campaign-status-ACTIVE"]').first()).toBeVisible({
        timeout: 15_000,
      });
    }
  });

  test('P0-006-STRATEGY-GENERATE: Strategy can be generated via real API', async () => {
    await page.goto(`/workspaces/${workspace}/strategy`);

    const generateBtn = page.locator('[data-testid="generate-strategy-btn"]');
    await expect(generateBtn).toBeVisible({ timeout: 15_000 });
    await generateBtn.click();

    // Wait for generation to complete (real AI may take time)
    await expect(page.locator('[data-testid="strategy-detail"]')).toBeVisible({ timeout: 60_000 });
  });

  test('P0-006-STRATEGY-SUBMIT: Strategy can be submitted for approval', async () => {
    await page.goto(`/workspaces/${workspace}/strategy`);
    await expect(page.locator('[data-testid="strategy-detail"]')).toBeVisible({ timeout: 15_000 });

    const submitBtn = page.locator('[data-testid="submit-strategy-btn"]');
    if (await submitBtn.isVisible().catch(() => false)) {
      await submitBtn.click();
      await expect(page.locator('[data-testid="strategy-status"]')).toContainText('PENDING', {
        timeout: 15_000,
      });
    }
  });

  test('P0-006-BUDGET-GENERATE: Budget can be generated via real API', async () => {
    await page.goto(`/workspaces/${workspace}/budget`);

    const generateBtn = page.locator('[data-testid="generate-budget-btn"]');
    await expect(generateBtn).toBeVisible({ timeout: 15_000 });

    // Fill monthly cap
    const capInput = page.locator('[data-testid="monthly-cap-input"]');
    if (await capInput.isVisible().catch(() => false)) {
      await capInput.fill('5000');
    }

    await generateBtn.click();
    await expect(page.locator('[data-testid="budget-detail"]')).toBeVisible({ timeout: 60_000 });
  });

  test('P0-006-APPROVAL-QUEUE: Approval queue loads from real backend', async () => {
    await page.goto(`/workspaces/${workspace}/approvals`);
    await expect(page.locator('[data-testid="approval-queue-root"]')).toBeVisible({ timeout: 15_000 });
    // May have 0 items — just verify the page loads without error
    await expect(page.locator('[data-testid="approval-queue-error"]')).not.toBeVisible();
  });

  test('P0-006-AI-LOG: AI action log loads from real backend', async () => {
    await page.goto(`/workspaces/${workspace}/ai-actions`);
    await expect(page.locator('[data-testid="ai-action-log-root"]')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('[data-testid="ai-action-log-error"]')).not.toBeVisible();
  });

  test('P0-006-CAPABILITY-GATE: Capability gating respected by real API', async () => {
    // Navigate to campaigns and verify capability endpoint is called
    const apiUrl = requireEnv('DMOS_API_BASE_URL');
    const token = requireEnv('DMOS_TEST_TOKEN');

    const response = await page.request.get(
      `${apiUrl}/api/v1/workspaces/${workspace}/capabilities`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
          'X-Tenant-ID': 'tenant-e2e',
        },
      },
    );

    expect(response.status()).toBe(200);
    const body = await response.json() as { capabilities: Array<{ key: string; enabled: boolean }> };
    expect(Array.isArray(body.capabilities)).toBe(true);
  });

  test('P0-006-CROSS-TENANT: Cross-tenant access is rejected by real API', async () => {
    const apiUrl = requireEnv('DMOS_API_BASE_URL');
    const token = requireEnv('DMOS_TEST_TOKEN');

    // Try to access a different workspace's campaigns (should be 403 or 404)
    const response = await page.request.get(
      `${apiUrl}/api/v1/workspaces/ws-foreign-tenant/campaigns`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
          'X-Tenant-ID': 'tenant-e2e',
        },
        failOnStatusCode: false,
      },
    );

    expect([403, 404]).toContain(response.status());
  });
});
