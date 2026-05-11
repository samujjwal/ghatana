/**
 * Real-backend release gate E2E tests for stable DMOS routes.
 *
 * These tests intentionally avoid page.route() mocks. CI starts the API and UI
 * before this suite runs, then the browser exercises the same HTTP client used
 * by the product UI.
 *
 * @doc.type test
 * @doc.purpose Stable route E2E coverage against a running DMOS backend
 * @doc.layer e2e
 */
import { expect, test, type Page } from '@playwright/test';

const apiBaseUrl = process.env['DMOS_API_BASE_URL'] ?? 'http://localhost:8080';
const tenant = process.env['DMOS_TEST_TENANT'] ?? 'tenant-e2e';
const principal = process.env['DMOS_TEST_PRINCIPAL'] ?? 'principal-e2e';
const token = process.env['DMOS_TEST_TOKEN'] ?? 'dev-e2e-token';
let workspace = process.env['DMOS_TEST_WORKSPACE'] ?? 'ws-e2e';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.evaluate((roles) => {
    sessionStorage.setItem('dmos_roles', JSON.stringify(roles));
  }, ['brand-manager', 'marketing-director']);
  await page.getByTestId('login-token').fill(token);
  await page.getByTestId('login-workspace-id').fill(workspace);
  await page.getByTestId('login-tenant-id').fill(tenant);
  await page.getByTestId('login-principal-id').fill(principal);
  await page.getByTestId('login-submit').click();
  await expect(page.getByTestId('dashboard-page')).toBeVisible({ timeout: 20_000 });
}

async function navigateInApp(page: Page, path: string): Promise<void> {
  await page.evaluate((targetPath) => {
    window.history.pushState({}, '', targetPath);
    window.dispatchEvent(new PopStateEvent('popstate', { state: {} }));
  }, path);
  await expect(page).toHaveURL(new RegExp(`${path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`));
}

function apiHeaders(idempotencyKey?: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    'X-Tenant-ID': tenant,
    'X-Principal-ID': principal,
    'X-Session-ID': 'session-e2e',
    'X-Roles': 'brand-manager,marketing-director',
    ...(idempotencyKey ? { 'X-Idempotency-Key': idempotencyKey } : {}),
  };
}

test.describe('Real backend stable routes', () => {
  test.beforeAll(async ({ request }) => {
    const response = await request.post(`${apiBaseUrl}/v1/workspaces`, {
      headers: apiHeaders(`workspace-${Date.now()}`),
      data: {
        name: `E2E Workspace ${Date.now()}`,
        description: 'Workspace created by the real-backend release gate.',
      },
    });
    expect(response.status()).toBe(201);
    const body = await response.json() as { id: string };
    workspace = body.id;
  });

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('dashboard consumes the backend summary API', async ({ page }) => {
    await navigateInApp(page, `/workspaces/${workspace}/dashboard`);
    await expect(page.getByTestId('dashboard-page')).toBeVisible({ timeout: 20_000 });

    const summary = await page.request.get(`${apiBaseUrl}/v1/workspaces/${workspace}/dashboard`, {
      headers: apiHeaders(),
    });
    expect(summary.status()).toBe(200);
    const body = await summary.json() as { metricSource: string; formulaVersion: string };
    expect(body.metricSource).toBe('DMOS_BACKEND_SUMMARY');
    expect(body.formulaVersion).toBe('dashboard-summary.v1');
  });

  test('campaigns route can create a campaign through the real API', async ({ page }) => {
    await navigateInApp(page, `/workspaces/${workspace}/campaigns`);
    await expect(page.getByTestId('campaigns-page')).toBeVisible({ timeout: 20_000 });

    const campaignName = `E2E Campaign ${Date.now()}`;
    await page.getByTestId('campaign-name-input').fill(campaignName);
    await page.getByTestId('campaign-type-select').selectOption('EMAIL');
    await page.getByTestId('campaign-objective-select').selectOption('LEADS');
    await page.getByTestId('campaign-budget-input').fill('500');
    await page.getByTestId('campaign-start-date-input').fill('2026-05-12');
    await page.getByTestId('campaign-end-date-input').fill('2026-06-12');
    await page.getByTestId('campaign-audience-input').fill('E2E test audience with explicit consent');
    await page.getByTestId('campaign-landing-page-input').fill('https://example.com/e2e');
    await page.getByTestId('create-campaign-btn').click();

    await expect(page.getByRole('cell', { name: campaignName })).toBeVisible({ timeout: 20_000 });
  });

  test('governance routes return explicit backend responses', async ({ page }) => {
    await navigateInApp(page, `/workspaces/${workspace}/approvals`);
    await expect(page.getByTestId('approval-queue-page')).toBeVisible({ timeout: 20_000 });

    await navigateInApp(page, `/workspaces/${workspace}/ai-actions`);
    await expect(page.getByTestId('ai-action-log-page')).toBeVisible({ timeout: 20_000 });

    const entitlements = await page.request.get(`${apiBaseUrl}/v1/route-entitlements`, {
      headers: apiHeaders(),
    });
    expect(entitlements.status()).toBe(200);
  });

  test('consent and suppression APIs enforce do-not-contact state', async ({ page }) => {
    const email = `e2e-${Date.now()}@example.com`;
    const subjectId = `subject-${Date.now()}`;
    const purpose = 'marketing-email';

    const consent = await page.request.post(`${apiBaseUrl}/v1/workspaces/${workspace}/consent`, {
      headers: apiHeaders(`consent-${Date.now()}`),
      data: { subjectId, purpose, granted: true },
    });
    expect(consent.status()).toBe(201);

    const unsubscribe = await page.request.post(`${apiBaseUrl}/v1/workspaces/${workspace}/unsubscribe`, {
      headers: apiHeaders(`unsubscribe-${Date.now()}`),
      data: { email, subjectId, purpose },
    });
    expect(unsubscribe.status()).toBe(200);

    const suppression = await page.request.get(
      `${apiBaseUrl}/v1/workspaces/${workspace}/suppression/check?email=${encodeURIComponent(email)}`,
      { headers: apiHeaders() },
    );
    expect(suppression.status()).toBe(200);
    const body = await suppression.json() as { suppressed: boolean };
    expect(body.suppressed).toBe(true);
  });
});
