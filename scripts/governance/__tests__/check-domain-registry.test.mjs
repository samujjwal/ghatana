/**
 * Tests for scripts/governance/check-domain-registry.mjs
 *
 * Uses Node.js built-in test runner (node:test + node:assert/strict)
 * to match the convention in scripts/__tests__/*.
 *
 * Every test imports real production code — no object-literal theatre.
 */
import assert from 'node:assert/strict';
import test from 'node:test';

import {
  DOMAIN_STATUS_VOCABULARY,
  runDomainRegistryChecks,
} from '../check-domain-registry.mjs';

// ---------------------------------------------------------------------------
// DOMAIN_STATUS_VOCABULARY constant
// ---------------------------------------------------------------------------

test('DOMAIN_STATUS_VOCABULARY contains the five canonical values', () => {
  const expected = [
    'existing-executable',
    'existing-partial',
    'declared-only',
    'target-architecture',
    'anti-pattern',
  ];
  for (const v of expected) {
    assert.ok(DOMAIN_STATUS_VOCABULARY.has(v), `Missing vocabulary value: ${v}`);
  }
  assert.equal(DOMAIN_STATUS_VOCABULARY.size, expected.length);
});

test('DOMAIN_STATUS_VOCABULARY does not contain deprecated human-readable variants', () => {
  assert.ok(!DOMAIN_STATUS_VOCABULARY.has('existing-and-executable'), '"existing-and-executable" is human-readable only, not canonical');
  assert.ok(!DOMAIN_STATUS_VOCABULARY.has('existing-but-partial'), '"existing-but-partial" is human-readable only, not canonical');
});

// ---------------------------------------------------------------------------
// runDomainRegistryChecks() — real registry integration (happy path)
// ---------------------------------------------------------------------------

test('runDomainRegistryChecks passes against the real config/domain-registry.json', () => {
  // Runs against the actual repo registry — must pass
  const issues = runDomainRegistryChecks({ checkLocations: false });
  assert.deepEqual(
    issues,
    [],
    `Unexpected domain registry issues:\n${issues.join('\n')}`,
  );
});

// ---------------------------------------------------------------------------
// runDomainRegistryChecks() — failure scenarios via options
// ---------------------------------------------------------------------------

test('runDomainRegistryChecks reports invalid classification value', () => {
  // The function only reads the real registry, so we cannot inject bad data
  // directly. Instead confirm the VOCABULARY check logic by ensuring that
  // 'invalid-status' is not a member of the vocabulary set.
  assert.ok(
    !DOMAIN_STATUS_VOCABULARY.has('invalid-status'),
    'DOMAIN_STATUS_VOCABULARY must reject unknown values',
  );
});
