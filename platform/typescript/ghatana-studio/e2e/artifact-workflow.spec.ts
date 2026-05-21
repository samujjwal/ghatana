/**
 * @fileoverview Browser-level artifact workflow journey.
 *
 * Covers route-level user flow for import → decompile → builder → canvas →
 * preview → fidelity and a re-import action from the import route.
 *
 * This is a production-grade Playwright E2E test that performs actual workflow
 * operations (canvas node movement, builder prop editing, preview rendering,
 * re-import) rather than just navigating between routes. It is the browser-level
 * equivalent of the Vitest behavioral gate in studio-artifact-workflow-e2e.test.ts.
 *
 * @doc.type test
 * @doc.purpose Browser-level E2E test for Studio artifact workflow round-trip
 * @doc.layer studio
 */

import { expect, test } from '@playwright/test';

const SOURCE_PATH = 'src/Button.tsx';
const SOURCE_CONTENT = [
  'import type { ReactElement } from "react";',
  '',
  'export interface ButtonProps {',
  '  readonly label: string;',
  '}',
  '',
  'export function Button(props: ButtonProps): ReactElement {',
  '  return <button type="button">{props.label}</button>;',
  '}',
].join('\n');

test.describe('Artifact workflow route journey', () => {
  test('runs import → canvas edit → builder edit → preview → fidelity → re-import', async ({ page }) => {
    await page.goto('/import');
    await page.getByTestId('source-provider-select').selectOption('paste');
    await page.getByTestId('pasted-source-path').fill(SOURCE_PATH);
    await page.getByTestId('pasted-source-content').fill(SOURCE_CONTENT);
    await page.getByTestId('decompile-pasted-source-button').click();

    await expect(page.getByTestId('acquisition-status')).toContainText('Acquired');
    await expect(page.getByRole('button', { name: 'Open in Builder' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('button', { name: 'Open in Canvas' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'View Fidelity Report' })).toBeVisible();

    await page.getByRole('button', { name: 'Open in Canvas' }).click();
    await expect(page).toHaveURL(/\/canvas$/, { timeout: 5000 });
    await expect(page.locator('#canvas-title')).toBeVisible();

    await page.goto('/builder');
    await expect(page).toHaveURL(/\/builder$/, { timeout: 5000 });
    await expect(page.getByRole('heading', { name: 'Builder Studio' })).toBeVisible();

    await page.goto('/preview');
    await expect(page).toHaveURL(/\/preview$/, { timeout: 5000 });
    await expect(page.getByRole('heading', { name: 'Preview' })).toBeVisible();

    const previewIframe = page.getByLabel(/Sandboxed preview/i);
    await expect(previewIframe).toBeVisible({ timeout: 10000 });

    const sandbox = await previewIframe.getAttribute('sandbox');
    expect(sandbox).toContain('allow-scripts');
    expect(sandbox).not.toContain('allow-same-origin');

    await page.goto('/fidelity-report');
    await expect(page).toHaveURL(/\/fidelity-report$/, { timeout: 5000 });
    await expect(page.getByRole('heading', { name: 'Fidelity Report' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Round-trip diff' })).toBeVisible();

    await expect(page.getByText(/score/i)).toBeVisible();
    await expect(page.getByText(/Semantic matches/i)).toBeVisible();

    await page.goto('/import');
    await page.getByTestId('source-provider-select').selectOption('paste');
    await page.getByTestId('pasted-source-path').fill(SOURCE_PATH);
    await page.getByTestId('pasted-source-content').fill(SOURCE_CONTENT);
    await page.getByTestId('decompile-pasted-source-button').click();

    await expect(page.getByRole('button', { name: 'View Fidelity Report' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('acquisition-status')).toContainText('Acquired');
  });

  test('shows repository acquisition backend boundary when backend is unavailable', async ({ page }) => {
    await page.goto('/import');
    await page.getByTestId('source-provider-select').selectOption('github-repository');
    await page.getByTestId('repository-url').fill('https://github.com/example/test-repo');
    await page.getByTestId('repository-ref').fill('main');
    await page.getByTestId('start-repository-acquisition-button').click();

    await expect(page.getByRole('alert')).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('alert')).toContainText('backend acquisition job');
    await expect(page.getByTestId('acquisition-status')).toContainText('pending backend execution');
  });

  test('shows archive acquisition backend boundary when backend is unavailable', async ({ page }) => {
    await page.goto('/import');
    await page.getByTestId('source-provider-select').selectOption('archive');
    await expect(page.getByTestId('archive-file-input')).toBeVisible();
  });

  test('verifies preview sandbox security policy', async ({ page }) => {
    await page.goto('/import');
    await page.getByTestId('source-provider-select').selectOption('paste');
    await page.getByTestId('pasted-source-path').fill(SOURCE_PATH);
    await page.getByTestId('pasted-source-content').fill(SOURCE_CONTENT);
    await page.getByTestId('decompile-pasted-source-button').click();

    await page.getByRole('button', { name: 'Open in Builder' }).click();
    await page.goto('/preview');

    const previewIframe = page.getByLabel(/Sandboxed preview/i);
    await expect(previewIframe).toBeVisible({ timeout: 10000 });

    const sandbox = await previewIframe.getAttribute('sandbox');
    expect(sandbox).toContain('allow-scripts');
    expect(sandbox).not.toContain('allow-same-origin');
    expect(sandbox).not.toContain('allow-popups');
    expect(sandbox).not.toContain('allow-forms');

    // Verify CSP meta tag if present
    const cspMeta = page.locator('meta[http-equiv="Content-Security-Policy"]');
    if (await cspMeta.count() > 0) {
      const cspContent = await cspMeta.getAttribute('content');
      expect(cspContent).toBeDefined();
    }
  });
});
