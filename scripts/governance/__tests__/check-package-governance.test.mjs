/**
 * Tests for scripts/governance/check-package-governance.mjs
 *
 * Uses Node.js built-in test runner (node:test + node:assert/strict).
 */
import assert from 'node:assert/strict';
import test from 'node:test';

import {
  DEPRECATED_PACKAGE_NAMES,
  runPackageGovernanceChecks,
} from '../check-package-governance.mjs';

// ---------------------------------------------------------------------------
// DEPRECATED_PACKAGE_NAMES constant
// ---------------------------------------------------------------------------

test('DEPRECATED_PACKAGE_NAMES includes the canonical legacy names', () => {
  const mustBeDeprecated = [
    '@ghatana/ui',
    '@ghatana/utils',
    '@ghatana/accessibility-audit',
    '@ghatana/audit-components',
    '@ghatana/canvas-core',
    '@ghatana/canvas-react',
    '@ghatana/canvas-plugins',
    '@ghatana/canvas-tools',
    '@ghatana/canvas-chrome',
  ];
  for (const name of mustBeDeprecated) {
    assert.ok(
      DEPRECATED_PACKAGE_NAMES.includes(name),
      `Expected "${name}" to be in DEPRECATED_PACKAGE_NAMES`,
    );
  }
});

test('DEPRECATED_PACKAGE_NAMES does not include canonical active package names', () => {
  const activePackages = [
    '@ghatana/design-system',
    '@ghatana/canvas',
    '@ghatana/platform-utils',
    '@ghatana/accessibility',
  ];
  for (const name of activePackages) {
    assert.ok(
      !DEPRECATED_PACKAGE_NAMES.includes(name),
      `"${name}" is an active canonical package and must not be deprecated`,
    );
  }
});

// ---------------------------------------------------------------------------
// Integration: real platform packages must pass governance
// ---------------------------------------------------------------------------

test('runPackageGovernanceChecks passes against the real platform/typescript tree', () => {
  const issues = runPackageGovernanceChecks();
  assert.deepEqual(
    issues,
    [],
    `Unexpected package governance issues:\n${issues.join('\n')}`,
  );
});
