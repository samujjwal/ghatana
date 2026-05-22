import assert from 'node:assert/strict';
import test from 'node:test';

import { checkAggregateGateIntegrity } from '../check-aggregate-gate-integrity.mjs';

test('passes when all referenced checks are defined', () => {
  const scripts = {
    'check:phase8': 'pnpm check:phase0 && pnpm check:phase1',
    'check:release-gate': 'pnpm check:phase8 && pnpm check:data-cloud-release-gate',
    'check:world-class-platform-readiness': 'pnpm check:release-gate',
    'check:phase0': 'node ./scripts/check-domain-boundaries.mjs',
    'check:phase1': 'node ./scripts/check-kernel-boundaries.mjs',
    'check:data-cloud-release-gate': 'pnpm check:data-cloud-ui-contracts',
    'check:data-cloud-ui-contracts': 'node ./scripts/check-product-ui-contracts.mjs',
  };

  const violations = checkAggregateGateIntegrity({ scripts });

  assert.deepEqual(violations, []);
});

test('fails when aggregate script is missing', () => {
  const scripts = {
    'check:phase8': 'pnpm check:phase0',
    'check:phase0': 'node ./scripts/check-domain-boundaries.mjs',
  };

  const violations = checkAggregateGateIntegrity({ scripts });

  assert.equal(
    violations.includes('check:release-gate: aggregate script is not defined'),
    true,
  );
});

test('fails when aggregate script references undefined checks', () => {
  const scripts = {
    'check:phase8': 'pnpm check:phase0 && pnpm check:missing-check',
    'check:release-gate': 'pnpm check:phase8',
    'check:world-class-platform-readiness': 'pnpm check:release-gate',
    'check:phase0': 'node ./scripts/check-domain-boundaries.mjs',
  };

  const violations = checkAggregateGateIntegrity({ scripts });

  assert.equal(
    violations.includes('check:phase8: references undefined script check:missing-check'),
    true,
  );
});

test('supports custom aggregate script set', () => {
  const scripts = {
    'check:alpha': 'pnpm check:beta',
    'check:beta': 'node ./scripts/some-script.mjs',
  };

  const violations = checkAggregateGateIntegrity({
    scripts,
    aggregateScripts: ['check:alpha'],
  });

  assert.deepEqual(violations, []);
});
