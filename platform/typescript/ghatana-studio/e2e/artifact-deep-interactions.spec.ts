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
    'export interface ButtonProps {',
    '  readonly label: string;',
    '}',
    'export function Button(props: ButtonProps) {',
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

    const node = canvas.getByTestId('artifact-canvas-node-Button');
    await expect(node).toBeVisible({ timeout: 10000 });
    const statePosition = page.getByTestId('artifact-canvas-state-position');
    const stateBefore = await statePosition.textContent();
    expect(stateBefore).not.toBeNull();

    const box = await node.boundingBox();
    expect(box).not.toBeNull();
    await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
    await page.mouse.down();
    await page.mouse.move(box!.x + box!.width / 2 + 96, box!.y + box!.height / 2 + 48, { steps: 8 });
    await page.mouse.up();

    await expect
      .poll(async () => statePosition.textContent())
      .not.toBe(stateBefore);
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

  test('blocks malformed Builder prop edits without corrupting the document', async ({ page }) => {
    await page.goto('/builder');
    await page.getByRole('button', { name: 'New Document', exact: true }).click();
    await expect(page.getByTestId('builder-visual-canvas')).toBeVisible();

    await page.getByTestId('builder-palette-item-Button').click();
    await page.getByTestId('builder-tree-node-Button').click();

    await expect(page.getByTestId('builder-property-value-variant')).toContainText('solid');
    await page.getByTestId('builder-property-value-variant').click();
    await page.getByTestId('builder-property-input-variant').fill('{"mode":');
    await page.getByTestId('builder-property-save-variant').click();

    await expect(page.getByTestId('builder-property-validation-variant')).toContainText('Enter valid JSON');
    await page.getByTestId('builder-property-cancel-variant').click();
    await expect(page.getByTestId('builder-property-value-variant')).toContainText('solid');
  });

  test('edits a Builder component through keyboard-accessible controls', async ({ page }) => {
    await page.goto('/builder');
    await page.getByRole('button', { name: 'New Document', exact: true }).click();
    await expect(page.getByTestId('builder-visual-canvas')).toBeVisible();

    await page.getByTestId('builder-palette-item-Button').focus();
    await page.keyboard.press('Enter');
    await page.getByTestId('builder-tree-node-Button').focus();
    await page.keyboard.press('Enter');

    await expect(page.getByTestId('builder-property-value-variant')).toContainText('solid');
    await page.getByTestId('builder-property-value-variant').focus();
    await page.keyboard.press('Enter');
    await page.getByTestId('builder-property-input-variant').fill('"outline"');
    await page.keyboard.press('Enter');

    await expect(page.getByTestId('builder-property-value-variant')).toContainText('outline');
  });

  test('updates Preview, Fidelity, and evidence after editing an imported Builder prop', async ({ page }) => {
    await importArtifact(page);
    await page.getByRole('button', { name: 'Open in Builder' }).click();

    await expect(page.getByRole('status', { name: 'Imported artifact active' })).toBeVisible();
    await page.getByTestId('builder-tree-node-Button').click();

    await expect(page.getByTestId('builder-property-value-label')).toBeVisible();
    await page.getByTestId('builder-property-value-label').click();
    await page.getByTestId('builder-property-input-label').fill('"Launch"');
    await page.getByTestId('builder-property-save-label').click();

    await expect(page.getByTestId('builder-property-value-label')).toContainText('Launch');
    await page.waitForFunction(() => {
      const persisted = window.localStorage.getItem('ghatana-studio-workflow-state');
      return persisted?.includes('Launch') === true && persisted.includes('builder-edit');
    });

    await page.goto('/preview');
    await expect(page.getByText('Ready', { exact: true })).toBeVisible({ timeout: 10000 });
    await expect
      .poll(async () => page.locator('iframe[title="Preview preview"]').getAttribute('srcdoc'))
      .toContain('Launch');

    await page.goto('/fidelity-report');
    await expect(page.getByTestId('fidelity-score')).toBeVisible();
    await expect(page.getByTestId('workflow-evidence-id')).toContainText('builder-edit');
    await expect(page.getByTestId('generated-validation-summary')).toContainText('Generated validation');
    await expect(page.getByText('BuilderEditedArtifact.tsx')).toBeVisible();
  });
});
