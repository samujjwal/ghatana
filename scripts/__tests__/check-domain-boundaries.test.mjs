import assert from 'node:assert/strict';
import test from 'node:test';

import { analyzeBoundaryViolations } from '../check-domain-boundaries.mjs';

const productIds = ['digital-marketing', 'phr', 'finance', 'flashit', 'data-cloud', 'yappc'];
const productPackageOwners = new Map([['@flashit/web', 'flashit']]);

test('platform importing product fails', () => {
  const violations = analyzeBoundaryViolations([
    {
      path: 'platform/typescript/kernel-lifecycle/src/Planner.ts',
      source: "import { x } from '../../../../products/flashit/web/src/index.js';",
    },
  ], { productIds, productPackageOwners });

  assert(violations.some((violation) => violation.includes('platform code imports product implementation path')));
});

test('product importing public @ghatana package passes', () => {
  const violations = analyzeBoundaryViolations([
    {
      path: 'products/flashit/web/src/App.tsx',
      source: "import { Button } from '@ghatana/design-system';",
    },
  ], { productIds, productPackageOwners });

  assert.deepEqual(violations, []);
});

test('kernel importing products yappc fails', () => {
  const violations = analyzeBoundaryViolations([
    {
      path: 'platform/typescript/kernel-lifecycle/src/Planner.ts',
      source: "import { y } from '../../../../products/yappc/core/runtime/index.js';",
    },
  ], { productIds, productPackageOwners });

  assert(violations.some((violation) => violation.includes('YAPPC implementation internals')));
});

test('kernel importing products data-cloud planes fails', () => {
  const violations = analyzeBoundaryViolations([
    {
      path: 'platform/typescript/kernel-lifecycle/src/Planner.ts',
      source: "import { y } from '../../../../products/data-cloud/planes/action/gateway/src/index.js';",
    },
  ], { productIds, productPackageOwners });

  assert(violations.some((violation) => violation.includes('Data Cloud plane internals')));
});