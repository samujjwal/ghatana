import assert from 'node:assert/strict';
import test from 'node:test';

import { findBoundaryViolations } from '../check-platform-product-boundaries.mjs';

const defaultRules = {
  productNames: ['yappc', 'data-cloud', 'phr', 'finance', 'flashit', 'tutorputor', 'dcmaar', 'digital-marketing'],
  productNeutralContractExceptions: [],
  legalBridgeLocations: [
    'products/data-cloud/extensions/kernel-bridge',
    'products/data-cloud/planes/action/kernel-bridge',
    'products/yappc/kernel-bridge',
    'products/digital-marketing/dm-kernel-bridge',
  ],
  allowlist: [],
};

test('platform imports product fails', () => {
  const files = [
    {
      path: 'platform/typescript/design-system/src/something.ts',
      source: "import { foo } from '../../../../products/phr/core/something.js';",
    },
  ];

  const violations = findBoundaryViolations(files, defaultRules);
  assert(violations.length > 0, 'expected at least one violation');
  assert(violations[0].includes('product path'), `expected product path violation, got: ${violations[0]}`);
});

test('product imports platform passes', () => {
  const files = [
    {
      path: 'products/digital-marketing/ui/src/hooks.ts',
      source: "import { something } from '@ghatana/design-system';",
    },
  ];

  const violations = findBoundaryViolations(files, defaultRules);
  assert.equal(violations.length, 0, 'expected no violations for product importing platform');
});

test('Data Cloud bridge location passes', () => {
  const files = [
    {
      path: 'products/data-cloud/extensions/kernel-bridge/src/Provider.ts',
      source: "import { KernelProvider } from '@ghatana/kernel-product-contracts';",
    },
  ];

  const violations = findBoundaryViolations(files, defaultRules);
  assert.equal(violations.length, 0, 'expected no violations for legal bridge location');
});

test('kernel imports YAPPC internals directly fails', () => {
  const files = [
    {
      path: 'platform/typescript/kernel-lifecycle/src/planner.ts',
      source: "import { YappcService } from '../../../../products/yappc/core/yappc-services/src/index.js';",
    },
  ];

  const violations = findBoundaryViolations(files, defaultRules);
  assert(violations.length > 0, 'expected at least one violation');
  assert(violations[0].includes('YAPPC internals'), `expected YAPPC internals violation, got: ${violations[0]}`);
});

test('kernel imports Data Cloud internals directly fails', () => {
  const files = [
    {
      path: 'platform/typescript/kernel-lifecycle/src/planner.ts',
      source: "import { DataPlane } from '../../../../products/data-cloud/planes/data/entity/src/index.js';",
    },
  ];

  const violations = findBoundaryViolations(files, defaultRules);
  assert(violations.length > 0, 'expected at least one violation');
  assert(violations[0].includes('Data Cloud internals'), `expected Data Cloud internals violation, got: ${violations[0]}`);
});

test('allowlist without reason fails validation', () => {
  const rules = {
    ...defaultRules,
    allowlist: [
      {
        path: 'platform/typescript/kernel-lifecycle/src/planner.ts',
        ruleId: 'kernel-no-yappc-internals',
        reason: '',
        owner: '',
        reviewBy: '',
      },
    ],
  };

  const files = [
    {
      path: 'platform/typescript/kernel-lifecycle/src/planner.ts',
      source: "import { YappcService } from '../../../../products/yappc/core/yappc-services/src/index.js';",
    },
  ];

  const violations = findBoundaryViolations(files, rules);
  assert(violations.length > 0, 'expected violation because allowlist entry has empty reason/owner/reviewBy');
});

test('valid allowlist entry suppresses violation', () => {
  const rules = {
    ...defaultRules,
    allowlist: [
      {
        path: 'platform/typescript/kernel-lifecycle/src/planner.ts',
        ruleId: 'platform-no-product-import',
        reason: 'Temporary migration shim for lifecycle provider',
        owner: 'platform-kernel',
        reviewBy: '2027-06-01',
      },
    ],
  };

  const files = [
    {
      path: 'platform/typescript/kernel-lifecycle/src/planner.ts',
      source: "import { something } from '../../../../products/digital-marketing/bridge.js';",
    },
  ];

  const violations = findBoundaryViolations(files, rules);
  assert.equal(violations.length, 0, 'expected no violations with valid allowlist entry');
});

test('product-prefixed platform package name fails', () => {
  const files = [
    {
      path: 'platform/typescript/yappc-helpers/package.json',
      source: JSON.stringify({ name: '@ghatana/yappc-helpers', version: '1.0.0' }),
    },
  ];

  const violations = findBoundaryViolations(files, defaultRules);
  assert(violations.length > 0, 'expected violation for product-prefixed platform package');
  assert(violations[0].includes('product-prefixed'), `expected product-prefixed violation, got: ${violations[0]}`);
});

test('platform Java code importing product package fails', () => {
  const files = [
    {
      path: 'platform/java/core/src/main/java/com/ghatana/core/Service.java',
      source: 'import com.ghatana.yappc.core.SomeService;',
    },
  ];

  const violations = findBoundaryViolations(files, defaultRules);
  assert(violations.length > 0, 'expected violation for Java platform importing product package');
});

test('non-platform file is not checked', () => {
  const files = [
    {
      path: 'products/yappc/core/src/index.ts',
      source: "import { foo } from '../../../products/data-cloud/something.js';",
    },
  ];

  const violations = findBoundaryViolations(files, defaultRules);
  assert.equal(violations.length, 0, 'expected no violations for non-platform files');
});
