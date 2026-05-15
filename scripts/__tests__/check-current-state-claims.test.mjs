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

test('executability claim without evidence fails', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing and executable\nThis service is production-ready.',
    },
  ]);

  assert(violations.some((v) => v.includes('lacks evidence')));
});

test('executability claim with evidence passes', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing and executable\nThis service is production-ready.\nEvidence: tests/unit/service.test.mjs',
    },
  ]);

  assert(!violations.some((v) => v.includes('lacks evidence')));
});

test('executability claim with CI evidence passes', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing and executable\nThis service is production-ready.\nCI: .github/workflows/ci.yml',
    },
  ]);

  assert(!violations.some((v) => v.includes('lacks evidence')));
});

test('executability claim with implementation evidence passes', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing and executable\nThis service is production-ready.\nImplementation: src/service.ts',
    },
  ]);

  assert(!violations.some((v) => v.includes('lacks evidence')));
});

test('non-executable claim does not require evidence', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Declared only\nThis is a target architecture.',
    },
  ]);

  assert(!violations.some((v) => v.includes('lacks evidence')));
});

test('partial claim does not require evidence', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing but partial\nThis is in progress.',
    },
  ]);

  assert(!violations.some((v) => v.includes('lacks evidence')));
});

test('false positive: claim with ref evidence passes', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing and executable\nThis service is production-ready.\nRef: docs/architecture/service.md',
    },
  ]);

  assert(!violations.some((v) => v.includes('lacks evidence')));
});

test('false positive: claim with validated by evidence passes', () => {
  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing and executable\nThis service is production-ready.\nValidated by: integration tests',
    },
  ]);

  assert(!violations.some((v) => v.includes('lacks evidence')));
});

test('domain registry integration test', () => {
  const domainRegistry = {
    domains: [
      {
        id: 'test-domain',
        classification: 'executable',
        currentStateEvidence: ['tests/unit/service.test.mjs']
      }
    ]
  };

  const violations = findCurrentStateClaimViolations([
    {
      path: 'docs/example.md',
      source: 'Current-state classification: Existing and executable\nThis service is production-ready.\nEvidence: tests/unit/service.test.mjs',
    },
  ], { domainRegistry });

  assert(!violations.some((v) => v.includes('lacks evidence')));
});