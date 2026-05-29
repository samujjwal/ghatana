/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

import { expect, test, type Page, type Response } from '@playwright/test';

/**
 * DC-A8: live browser journeys against a real launcher backend.
 *
 * These tests intentionally avoid request mocking so the UI exercises
 * actual launcher endpoints started by Playwright webServer config.
 *
 * @doc.type test
 * @doc.purpose Live launcher browser journeys for core operator paths
 * @doc.layer testing
 */

async function bootstrapTenantIfPrompted(page: Page): Promise<void> {
  await page.goto('/');

  const tenantInput = page.locator('input[placeholder*="tenant" i]');
  if (await tenantInput.isVisible()) {
    await tenantInput.fill('dc-live-e2e-tenant');
    const continueButton = page.locator('button:has-text("Continue")');
    if (await continueButton.isVisible()) {
      await continueButton.click();
    }
  }

  await expect(page.locator('body')).toBeVisible();
}

async function waitForApiResponse(
  page: Page,
  matcher: (response: Response) => boolean,
  timeoutMs = 20_000,
): Promise<Response> {
  return page.waitForResponse((response) => {
    if (!response.url().includes('/api/v1/')) {
      return false;
    }
    return matcher(response);
  }, { timeout: timeoutMs });
}

test.describe('Live Launcher Journeys', () => {
  test.beforeEach(async ({ page }) => {
    await bootstrapTenantIfPrompted(page);
  });

  test('Intelligent Hub renders with real launcher session context', async ({ page }) => {
    await page.goto('/hub');

    await expect(page.getByTestId('intelligent-hub-page')).toBeVisible();
    await expect(page.getByTestId('intelligent-hub-header')).toBeVisible();
    await expect(page.getByTestId('intelligent-hub-outcome-section')).toBeVisible();
  });

  test('Workflows journey fetches live pipeline list from launcher', async ({ page }) => {
    const pipelinesResponsePromise = waitForApiResponse(page, (response) => {
      return response.request().method() === 'GET' && (
        response.url().includes('/api/v1/action/pipelines') ||
        response.url().includes('/api/v1/pipelines')
      );
    });

    await page.goto('/pipelines');

    await expect(page.getByTestId('workflows-page')).toBeVisible();
    await expect(page.getByTestId('create-pipeline-button')).toBeVisible();

    const pipelinesResponse = await pipelinesResponsePromise;
    expect(pipelinesResponse.status()).toBeLessThan(500);
  });

  test('SQL Workspace journey executes a live analytics query call', async ({ page }) => {
    await page.goto('/query');

    await expect(page.getByTestId('sql-workspace-page')).toBeVisible();
    await expect(page.getByTestId('sql-run-query')).toBeVisible();

    const queryResponsePromise = waitForApiResponse(page, (response) => {
      return response.request().method() === 'POST' && response.url().includes('/api/v1/analytics/query');
    });

    await page.getByTestId('sql-run-query').click();

    const queryResponse = await queryResponsePromise;
    expect(queryResponse.status()).toBeLessThan(500);

    const hasResults = await page.getByTestId('sql-query-results').isVisible().catch(() => false);
    const hasError = await page.getByTestId('sql-query-error').isVisible().catch(() => false);
    expect(hasResults || hasError).toBeTruthy();
  });

  test('Trust Center journey reaches live governance endpoints', async ({ page }) => {
    const trustResponsePromise = waitForApiResponse(page, (response) => {
      const url = response.url();
      return (
        url.includes('/governance') ||
        url.includes('/compliance') ||
        url.includes('/retention') ||
        url.includes('/policy') ||
        url.includes('/audit')
      );
    });

    await page.goto('/trust');

    await expect(page.getByTestId('trust-center-page')).toBeVisible();
    await expect(page.getByTestId('trust-lifecycle-section')).toBeVisible();

    const trustResponse = await trustResponsePromise;
    expect(trustResponse.status()).toBeLessThan(500);
  });

  test('Alerts journey reaches live alerts endpoints', async ({ page }) => {
    const alertsResponsePromise = waitForApiResponse(page, (response) => {
      return response.url().includes('/api/v1/alerts');
    });

    await page.goto('/alerts');

    await expect(page.getByTestId('alerts-page')).toBeVisible();

    const alertsResponse = await alertsResponsePromise;
    expect(alertsResponse.status()).toBeLessThan(500);

    const hasGroupedSection = await page.getByTestId('alerts-grouped-section').isVisible().catch(() => false);
    const hasBoundary = await page.getByTestId('alerts-ai-correlations-boundary').isVisible().catch(() => false);
    expect(hasGroupedSection || hasBoundary).toBeTruthy();
  });
});
