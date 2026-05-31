import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import assert from 'node:assert/strict';

import { scanKernelProductAccessibility } from '../lib/kernel-product-accessibility-plugin.mjs';
import { scanKernelProductI18nConformance } from '../lib/kernel-product-i18n-plugin.mjs';

test('Kernel product i18n plugin detects raw visible strings generically', () => {
  const root = mkdtempSync(join(tmpdir(), 'kernel-i18n-'));
  try {
    mkdirSync(join(root, 'src'), { recursive: true });
    writeFileSync(join(root, 'src', 'Page.tsx'), `
      export function Page() {
        return (
          <h1>Raw Visible Heading</h1>
        );
      }
    `);

    const result = scanKernelProductI18nConformance({
      repoRoot: root,
      productLabel: 'Sample',
      scanDirs: ['src'],
    });

    assert.equal(result.violations.length, 1);
    assert.equal(result.violations[0].description, 'Raw English JSX text node');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('Kernel product accessibility plugin detects unlabeled interactive elements generically', () => {
  const root = mkdtempSync(join(tmpdir(), 'kernel-a11y-'));
  try {
    mkdirSync(join(root, 'src'), { recursive: true });
    writeFileSync(join(root, 'src', 'Widget.tsx'), 'export function Widget() { return <button></button>; }');

    const result = scanKernelProductAccessibility({
      repoRoot: root,
      productLabel: 'Sample',
      scanDirs: ['src'],
    });

    assert.equal(result.issues.length, 1);
    assert.equal(result.issues[0].type, 'button-without-label');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
