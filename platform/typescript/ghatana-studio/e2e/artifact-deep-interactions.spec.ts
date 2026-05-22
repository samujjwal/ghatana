/**
 * @fileoverview Deep browser interactions for artifact Canvas and Builder.
 *
 * Exercises real graph node movement and Builder property mutation through
 * stable test contracts instead of route-level smoke navigation.
 */

import { expect, test } from '@playwright/test';

async function importArtifact(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/import');
  await page.getByTestId('source-provider-select').selectOption('paste');
  await page.getByTestId('pasted-source-path').fill('src/Button.tsx');
  await page.getByTestId('pasted-source-content').fill([
    'export function Button(props: { readonly label: string }) {',
    '  return <button type="button">{props.label}</button>;',
    '}',
  ].join('\n'));
  await page.getByTestId('decompile-pasted-source-button').click();
  await expect(page.getByRole('button', { name: 'Open in Canvas' })).toBeVisible({ timeout: 10000 });
}

test.describe('Artifact deep Canvas and Builder interactions', () => {
  test('moves a canvas graph node through drag interaction', async ({ page }) => {
    await importArtifact(page);
    await page.getByRole('button', { name: 'Open in Canvas' }).click();

    const canvas = page.getByTestId('artifact-graph-canvas');
    await expect(canvas).toBeVisible();

    const node = page.locator('.react-flow__node').first();
    await expect(node).toBeVisible({ timeout: 10000 });
    const before = await node.evaluate((element) => getComputedStyle(element).transform);

    const box = await node.boundingBox();
    expect(box).not.toBeNull();
    await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
    await page.mouse.down();
    await page.mouse.move(box!.x + box!.width / 2 + 96, box!.y + box!.height / 2 + 48, { steps: 8 });
    await page.mouse.up();

    await expect
      .poll(async () => node.evaluate((element) => getComputedStyle(element).transform))
      .not.toBe(before);
  });

  test('adds a Builder component and edits an exposed property', async ({ page }) => {
    await page.goto('/builder');
    await page.getByRole('button', { name: 'New Document', exact: true }).click();
    await expect(page.getByTestId('builder-visual-canvas')).toBeVisible();

    await page.getByTestId('builder-palette-item-Button').click();
    await page.getByTestId('builder-tree-node-Button').click();

    await expect(page.getByTestId('builder-property-value-variant')).toContainText('solid');
    await page.getByTestId('builder-property-value-variant').click();
    await page.getByTestId('builder-property-input-variant').fill('"outline"');
    await page.getByTestId('builder-property-save-variant').click();

    await expect(page.getByTestId('builder-property-value-variant')).toContainText('outline');
  });
});
