import assert from 'node:assert/strict';
import test from 'node:test';

import { findCurrentStateClaimViolations } from '../check-current-state-claims.mjs';

test('unclassified current-state claim fails', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'This service is production-ready and complete.',
    },
  ]);

  assert.equal(violations.length, 1);
});

test('classified claim passes', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing and executable\nThis service is production-ready.',
    },
  ]);

  assert.deepEqual(violations, []);
});

test('target architecture section passes', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Target architecture\nThis platform supports multi-product rollout.',
    },
  ]);

  assert.deepEqual(violations, []);
});