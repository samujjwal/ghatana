import assert from 'node:assert/strict';
import test from 'node:test';

import { findDeprecatedPackageImports } from '../check-deprecated-packages.mjs';

test('deprecated import fails', () => {
  const files = [
    {
      path: 'products/digital-marketing/ui/src/App.tsx',
      source: "import { Button } from '@ghatana/ui';",
    },
  ];

  const violations = findDeprecatedPackageImports(files);
  assert(violations.length > 0, 'expected at least one violation');
  assert(violations[0].includes("'@ghatana/ui'"), `expected @ghatana/ui violation, got: ${violations[0]}`);
  assert(violations[0].includes('@ghatana/design-system'), 'expected replacement suggestion');
});

test('valid canonical import passes', () => {
  const files = [
    {
      path: 'products/digital-marketing/ui/src/App.tsx',
      source: "import { Button } from '@ghatana/design-system';",
    },
  ];

  const violations = findDeprecatedPackageImports(files);
  assert.equal(violations.length, 0, 'expected no violations for canonical import');
});

test('deprecated canvas-core import fails', () => {
  const files = [
    {
      path: 'platform/typescript/canvas/src/index.ts',
      source: "import { Canvas } from '@ghatana/canvas-core';",
    },
  ];

  const violations = findDeprecatedPackageImports(files);
  assert(violations.length > 0, 'expected at least one violation for deprecated canvas-core');
});

test('product-prefixed import in platform code fails', () => {
  const files = [
    {
      path: 'platform/typescript/kernel-lifecycle/src/registry.ts',
      source: "import { YappcConfig } from '@ghatana/yappc-config';",
    },
  ];

  const violations = findDeprecatedPackageImports(files);
  assert(violations.length > 0, 'expected violation for product-prefixed import in platform');
  assert(violations[0].includes('product-prefixed'), `expected product-prefixed violation, got: ${violations[0]}`);
});

test('product-prefixed import in product code is allowed', () => {
  const files = [
    {
      path: 'products/yappc/frontend/src/index.ts',
      source: "import { YappcConfig } from '@ghatana/yappc-config';",
    },
  ];

  const violations = findDeprecatedPackageImports(files);
  assert.equal(violations.length, 0, 'product-prefixed imports within product code should be allowed');
});

test('deprecated dependency in package.json fails', () => {
  const files = [
    {
      path: 'products/phr/apps/web/package.json',
      source: JSON.stringify({
        name: '@ghatana/phr-web',
        dependencies: { '@ghatana/ui': '^1.0.0' },
      }),
    },
  ];

  const violations = findDeprecatedPackageImports(files);
  assert(violations.length > 0, 'expected violation for deprecated package dependency');
  assert(violations[0].includes('deprecated package dependency'), `expected dependency violation, got: ${violations[0]}`);
});

test('relative imports are not checked as package names', () => {
  const files = [
    {
      path: 'platform/typescript/design-system/src/Button.ts',
      source: "import { theme } from './theme.js';",
    },
  ];

  const violations = findDeprecatedPackageImports(files);
  assert.equal(violations.length, 0, 'relative imports should not trigger violations');
});
