import assert from 'node:assert/strict';
import test from 'node:test';

import { findDeprecatedImports } from '../check-deprecated-imports.mjs';

test('deprecated import fails with canonical replacement', () => {
  const violations = findDeprecatedImports([
    {
      path: 'products/flashit/web/src/App.tsx',
      source: "import { Button } from '@ghatana/ui';",
    },
  ]);

  assert.equal(violations.length, 1);
  assert.match(violations[0], /@ghatana\/design-system/);
});

test('canonical import passes', () => {
  const violations = findDeprecatedImports([
    {
      path: 'products/flashit/web/src/App.tsx',
      source: "import { Button } from '@ghatana/design-system';",
    },
  ]);

  assert.deepEqual(violations, []);
});