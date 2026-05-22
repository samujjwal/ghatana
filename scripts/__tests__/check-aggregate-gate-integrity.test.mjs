import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
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
  const rootDir = mkdtempSync(path.join(tmpdir(), 'aggregate-gate-integrity-'));
  try {
    mkdirSync(path.join(rootDir, 'scripts'), { recursive: true });
    writeFileSync(path.join(rootDir, 'scripts', 'some-script.mjs'), '');
    const scripts = {
      'check:alpha': 'pnpm check:beta',
      'check:beta': 'node ./scripts/some-script.mjs',
    };

    const violations = checkAggregateGateIntegrity({
      scripts,
      aggregateScripts: ['check:alpha'],
      rootDir,
    });

    assert.deepEqual(violations, []);
  } finally {
    rmSync(rootDir, { recursive: true, force: true });
  }
});

test('validates node scripts, pnpm directories, and test runner target paths recursively', () => {
  const rootDir = mkdtempSync(path.join(tmpdir(), 'aggregate-gate-integrity-'));
  try {
    mkdirSync(path.join(rootDir, 'scripts', '__tests__'), { recursive: true });
    mkdirSync(path.join(rootDir, 'packages', 'studio', 'src', '__tests__'), { recursive: true });
    writeFileSync(path.join(rootDir, 'scripts', 'real-check.mjs'), '');
    writeFileSync(path.join(rootDir, 'packages', 'studio', 'src', '__tests__', 'real.test.ts'), '');

    const scripts = {
      'check:phase8': 'pnpm check:phase8:fast',
      'check:phase8:fast': 'node ./scripts/real-check.mjs && pnpm --dir packages/studio exec vitest run src/__tests__/real.test.ts',
      'check:release-gate': 'pnpm check:phase8',
      'check:world-class-platform-readiness': 'pnpm check:release-gate',
    };

    const violations = checkAggregateGateIntegrity({ scripts, rootDir });

    assert.deepEqual(violations, []);
  } finally {
    rmSync(rootDir, { recursive: true, force: true });
  }
});

test('fails when referenced node scripts, pnpm directories, or test targets are missing', () => {
  const rootDir = mkdtempSync(path.join(tmpdir(), 'aggregate-gate-integrity-'));
  try {
    const scripts = {
      'check:phase8': 'node ./scripts/missing.mjs && pnpm --dir packages/missing exec vitest run src/__tests__/missing.test.ts',
      'check:release-gate': 'pnpm check:phase8',
      'check:world-class-platform-readiness': 'pnpm check:release-gate',
    };

    const violations = checkAggregateGateIntegrity({ scripts, rootDir });

    assert.equal(violations.some((violation) => violation.includes('node script does not exist: ./scripts/missing.mjs')), true);
    assert.equal(violations.some((violation) => violation.includes('pnpm --dir target does not exist: packages/missing')), true);
    assert.equal(violations.some((violation) => violation.includes('vitest target does not exist: src/__tests__/missing.test.ts')), true);
  } finally {
    rmSync(rootDir, { recursive: true, force: true });
  }
});

test('detects recursive aggregate gate cycles', () => {
  const scripts = {
    'check:phase8': 'pnpm check:release-gate',
    'check:release-gate': 'pnpm check:world-class-platform-readiness',
    'check:world-class-platform-readiness': 'pnpm check:phase8',
  };

  const violations = checkAggregateGateIntegrity({ scripts });

  assert.equal(
    violations.includes('check:phase8: recursive aggregate script cycle detected: check:phase8 -> check:release-gate -> check:world-class-platform-readiness -> check:phase8'),
    true,
  );
});

test('validates gradle task project directories', () => {
  const rootDir = mkdtempSync(path.join(tmpdir(), 'aggregate-gate-integrity-'));
  try {
    mkdirSync(path.join(rootDir, 'platform-kernel', 'kernel-plugin'), { recursive: true });
    const scripts = {
      'check:phase8': 'node ./scripts/run-gradle-wrapper.mjs :platform-kernel:kernel-plugin:test',
      'check:release-gate': 'pnpm check:phase8 && node ./scripts/run-gradle-wrapper.mjs :missing:project:test',
      'check:world-class-platform-readiness': 'pnpm check:release-gate',
    };
    mkdirSync(path.join(rootDir, 'scripts'), { recursive: true });
    writeFileSync(path.join(rootDir, 'scripts', 'run-gradle-wrapper.mjs'), '');

    const violations = checkAggregateGateIntegrity({ scripts, rootDir });

    assert.equal(violations.some((violation) => violation.includes(':missing:project:test')), true);
    assert.equal(violations.some((violation) => violation.includes(':platform-kernel:kernel-plugin:test')), false);
  } finally {
    rmSync(rootDir, { recursive: true, force: true });
  }
});
