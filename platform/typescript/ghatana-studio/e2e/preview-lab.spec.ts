/**
 * @fileoverview E2E tests for the Preview route.
 *
 * The current preview page renders from router/workflow state and exposes a
 * sandboxed iframe only when source is available. These tests verify the
 * production route surface instead of the retired PreviewLab controls.
 *
 * @doc.type test
 * @doc.purpose Preview route E2E tests
 * @doc.layer studio
 */

import { expect, test } from '@playwright/test';

test.describe('Preview Workflow', () => {
  test('loads Preview route in empty state', async ({ page }) => {
    await page.goto('/preview');

    await expect(page.getByRole('heading', { name: 'Preview' })).toBeVisible();
    await expect(page.getByText('No preview source available')).toBeVisible();
    await expect(page.getByText('Navigate here from a Builder export to see a live preview.')).toBeVisible();
  });

  test('renders a sandboxed preview from router state', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => {
      window.history.pushState(
        {
          usr: {
            title: 'E2E Preview',
            source: `
              import React from 'react';
              export default function PreviewFixture() {
                console.log('preview ready');
                return <main><h1>E2E Preview Fixture</h1><button>Click me</button></main>;
              }
            `,
          },
          key: 'e2e-preview',
          idx: 1,
        },
        '',
        '/preview',
      );
    });
    await page.reload();

    await expect(page.getByRole('heading', { name: 'E2E Preview' })).toBeVisible();
    await expect(page.getByRole('status', { name: /ready/i }).or(page.getByText('Ready'))).toBeVisible();
    const previewFrame = page
      .frameLocator('iframe[title="E2E Preview preview"]')
      .frameLocator('iframe[title="Preview sandbox"]');
    await expect(previewFrame.getByRole('heading', { name: 'E2E Preview Fixture' })).toBeVisible();
    await expect(page.locator('iframe[title="E2E Preview preview"]')).toHaveAttribute('sandbox', 'allow-scripts');
  });

  test('refreshes an available preview', async ({ page }) => {
    await page.goto('/');
    await page.evaluate(() => {
      window.history.pushState(
        {
          usr: {
            title: 'Refresh Preview',
            source: 'export default function App() { return <section><h1>Refresh Fixture</h1></section>; }',
          },
          key: 'e2e-refresh-preview',
          idx: 1,
        },
        '',
        '/preview',
      );
    });
    await page.reload();

    await expect(page.getByText('Ready')).toBeVisible();
    await page.getByRole('button', { name: 'Refresh preview' }).click();
    await expect(
      page
        .frameLocator('iframe[title="Refresh Preview preview"]')
        .frameLocator('iframe[title="Preview sandbox"]')
        .getByRole('heading', { name: 'Refresh Fixture' }),
    ).toBeVisible();
  });
});
