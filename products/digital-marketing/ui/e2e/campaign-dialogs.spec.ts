/**
 * E2E: Campaign destructive and duplicate dialog flows.
 *
 * @doc.type test
 * @doc.purpose Verify design-system destructive confirmation and duplicate validation journeys
 * @doc.layer e2e
 */
import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { loginAs, mockDmosApi, navigateInApp, TEST_WORKSPACE } from './fixtures';

type CampaignStatus = 'DRAFT' | 'LAUNCHED' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';

interface CampaignFixture {
  id: string;
  workspaceId: string;
  name: string;
  status: CampaignStatus;
  type: 'EMAIL' | 'SOCIAL' | 'PAID_SEARCH';
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

function buildCampaign(
  id: string,
  name: string,
  status: CampaignStatus,
): CampaignFixture {
  return {
    id,
    workspaceId: TEST_WORKSPACE,
    name,
    status,
    type: 'EMAIL',
    createdBy: 'test-user',
    createdAt: '2026-01-10T10:00:00Z',
    updatedAt: '2026-01-10T10:00:00Z',
  };
}

async function mockCampaignEndpoints(page: Page): Promise<void> {
  const campaigns: CampaignFixture[] = [
    buildCampaign('cmp-complete-1', 'Completed Archive Target', 'COMPLETED'),
    buildCampaign('cmp-draft-1', 'Draft Duplicate Target', 'DRAFT'),
  ];

  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/campaigns**`,
    (route) => {
      if (route.request().method() !== 'GET') {
        return route.fallback();
      }

      const url = new URL(route.request().url());
      const offset = Number(url.searchParams.get('offset') ?? '0');

      return route.fulfill({
        json: {
          items: campaigns,
          count: campaigns.length,
          offset,
        },
      });
    },
  );

  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/campaigns/cmp-complete-1/archive`,
    (route) => {
      const target = campaigns.find((campaign) => campaign.id === 'cmp-complete-1');
      if (target) {
        target.status = 'ARCHIVED';
        target.updatedAt = new Date().toISOString();
      }

      return route.fulfill({ json: target });
    },
  );

  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/campaigns/cmp-draft-1/duplicate`,
    async (route) => {
      const bodyText = route.request().postData() ?? '{}';
      const parsed = JSON.parse(bodyText) as { name?: string };
      const duplicateName = (parsed.name ?? '').trim();

      if (!duplicateName) {
        return route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'name is required' }),
        });
      }

      const duplicated = buildCampaign(`cmp-copy-${campaigns.length + 1}`, duplicateName, 'DRAFT');
      campaigns.push(duplicated);
      return route.fulfill({ json: duplicated });
    },
  );
}

test.describe('campaign dialogs', () => {
  test.beforeEach(async ({ page }) => {
    await mockDmosApi(page);
    await mockCampaignEndpoints(page);
    await loginAs(page, { roles: ['brand-manager'] });
    await page.waitForURL(new RegExp(`/workspaces/${TEST_WORKSPACE}/dashboard`));
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/campaigns`);
    await expect(page.locator('[data-testid="campaigns-page"]')).toBeVisible();
  });

  test('shows accessible archive confirmation dialog and archives on confirm', async ({ page }) => {
    await page.click('[data-testid="archive-campaign-cmp-complete-1"]');

    const archiveDialog = page.locator('[data-testid="archive-dialog"]');
    await expect(archiveDialog).toBeVisible();
    await expect(page.getByRole('heading', { name: /archive campaign/i })).toBeVisible();
    await expect(archiveDialog).toContainText('destructive');

    await page.click('[data-testid="archive-confirm-btn"]');

    await expect(page.locator('[data-testid="campaign-row-cmp-complete-1"]')).toContainText('ARCHIVED');
  });

  test('validates duplicate name and creates duplicate after confirmation', async ({ page }) => {
    await page.click('[data-testid="duplicate-campaign-cmp-draft-1"]');

    const duplicateDialog = page.locator('[data-testid="duplicate-dialog"]');
    await expect(duplicateDialog).toBeVisible();
    await expect(page.getByRole('heading', { name: /duplicate campaign/i })).toBeVisible();
    await expect(page.getByLabel('Duplicate Name')).toBeVisible();

    await page.fill('[data-testid="duplicate-name-input"]', '');
    await page.click('[data-testid="duplicate-confirm-btn"]');
    await expect(page.locator('[data-testid="duplicate-name-error"]')).toBeVisible();

    await page.fill('[data-testid="duplicate-name-input"]', 'Duplicated Campaign Name');
    await page.click('[data-testid="duplicate-confirm-btn"]');

    await expect(page.locator('[data-testid="campaign-row-cmp-copy-3"]')).toContainText('Duplicated Campaign Name');
  });
});
