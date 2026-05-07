import { expect, test } from '@playwright/test';

const ONBOARDING_COMPLETE_KEY = 'dc:onboarding:complete';
const SESSION_TENANT_KEY = 'dc:session:tenantId';
const SESSION_API_BASE_URL_KEY = 'dc:session:apiBaseUrl';
const COMPAT_TENANT_KEY = 'tenantId';
const PLAYWRIGHT_TENANT_ID = 'playwright-tenant';
const API_BASE_URL = 'http://127.0.0.1:8082';

test.describe('Data Explorer smoke', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(
      ({ apiBaseUrl, apiBaseUrlKey, compatibilityTenantKey, onboardingCompleteKey, tenantId, tenantKey }) => {
        localStorage.setItem(onboardingCompleteKey, 'true');
        localStorage.setItem(compatibilityTenantKey, tenantId);
        sessionStorage.setItem(tenantKey, tenantId);
        sessionStorage.setItem(apiBaseUrlKey, apiBaseUrl);
      },
      {
        apiBaseUrl: API_BASE_URL,
        apiBaseUrlKey: SESSION_API_BASE_URL_KEY,
        compatibilityTenantKey: COMPAT_TENANT_KEY,
        onboardingCompleteKey: ONBOARDING_COMPLETE_KEY,
        tenantId: PLAYWRIGHT_TENANT_ID,
        tenantKey: SESSION_TENANT_KEY,
      },
    );
  });

  test('creates and lists a real collection through the live launcher', async ({ page, request }) => {
    const uniqueSuffix = Date.now().toString();
    const collectionName = `playwright-orders-${uniqueSuffix}`;
    const schemaName = `playwrightSchema${uniqueSuffix}`;

    await page.goto('/data/new');

    await expect(page.getByRole('heading', { name: 'Create New Collection' })).toBeVisible();
    await page.locator('#name').fill(collectionName);
    await page.locator('#schema\\.name').fill(schemaName);
    await page.locator('#description').fill('Real launcher-backed browser smoke collection');
    await page.getByRole('button', { name: 'Save Collection' }).click();

    await expect(page).toHaveURL(/\/data$/);
    await expect(page.getByTestId('data-explorer-page')).toBeVisible();
    await expect(page.getByText(collectionName)).toBeVisible();

    const response = await request.get('http://127.0.0.1:8082/api/v1/entities/dc_collections?search=' + encodeURIComponent(collectionName), {
      headers: {
        'Accept': 'application/json',
        'X-Tenant-ID': PLAYWRIGHT_TENANT_ID,
      },
    });

    expect(response.ok()).toBeTruthy();
    const payload = await response.json() as { entities: Array<{ data?: { name?: string } }> };
    expect(payload.entities.some((entity) => entity.data?.name === collectionName)).toBeTruthy();
  });
});