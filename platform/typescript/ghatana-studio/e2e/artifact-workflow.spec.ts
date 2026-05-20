/**
 * @fileoverview Browser-level artifact workflow journey.
 *
 * Covers route-level user flow for import → decompile → builder → canvas →
 * preview → fidelity and a re-import action from the import route.
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
  test('runs import → builder → canvas → preview → fidelity with re-import', async ({ page }) => {
    await page.goto('/import');

    await page.getByLabel('Source provider').selectOption('paste');
    await page.getByLabel('Source path').fill(SOURCE_PATH);
    await page.getByLabel('Source content').fill(SOURCE_CONTENT);
    await page.getByRole('button', { name: 'Decompile pasted source' }).click();

    await expect(page.getByRole('button', { name: 'Open in Builder' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Open in Canvas' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'View Fidelity Report' })).toBeVisible();

    await page.getByRole('button', { name: 'Open in Builder' }).click();
    await expect(page).toHaveURL(/\/builder$/);
    await expect(page.getByRole('heading', { name: 'Builder Studio' })).toBeVisible();

    await page.goto('/canvas');
    await expect(page).toHaveURL(/\/canvas$/);
    await expect(page.locator('#canvas-title')).toBeVisible();

    const firstNode = page.locator('.react-flow__node').first();
    await expect(firstNode).toBeVisible();
    await firstNode.click();
    await expect(firstNode).toHaveClass(/selected/);

    await page.goto('/preview');
    await expect(page).toHaveURL(/\/preview$/);
    await expect(page.getByRole('heading', { name: 'Preview' })).toBeVisible();
    const iframeCount = await page.locator('iframe[title="Preview preview"]').count();
    if (iframeCount > 0) {
      await expect(page.locator('iframe[title="Preview preview"]')).toBeVisible();
    } else {
      await expect(page.getByText('No preview source available')).toBeVisible();
    }

    await page.goto('/fidelity-report');
    await expect(page).toHaveURL(/\/fidelity-report$/);
    await expect(page.getByRole('heading', { name: 'Fidelity Report' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Round-trip diff' })).toBeVisible();

    await page.goto('/import');
    await page.getByLabel('Source provider').selectOption('paste');
    await page.getByLabel('Source path').fill(SOURCE_PATH);
    await page.getByLabel('Source content').fill(SOURCE_CONTENT);
    await page.getByRole('button', { name: 'Decompile pasted source' }).click();

    await expect(page.getByRole('button', { name: 'View Fidelity Report' })).toBeVisible();
    await expect(page.getByText(/requires backend acquisition job/i)).toHaveCount(0);
  });
});
