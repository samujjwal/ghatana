import assert from 'node:assert/strict';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { tmpdir } from 'node:os';
import test from 'node:test';

import { findOrphanModules } from '../check-orphan-modules.mjs';

function createFixture(structure) {
  const root = path.join(tmpdir(), `orphan-test-${Date.now()}-${Math.random().toString(36).slice(2)}`);
  mkdirSync(root, { recursive: true });

  for (const [filePath, content] of Object.entries(structure)) {
    const fullPath = path.join(root, filePath);
    mkdirSync(path.dirname(fullPath), { recursive: true });
    writeFileSync(fullPath, content);
  }

  return root;
}

function cleanFixture(root) {
  try {
    rmSync(root, { recursive: true, force: true });
  } catch {
    // Ignore cleanup errors
  }
}

test('folder-only shell fails', () => {
  const root = createFixture({
    'platform/typescript/my-shell/index.ts': 'export {};',
  });

  try {
    const { violations } = findOrphanModules({ repoRoot: root, allowlist: { allowlist: [] } });
    assert(violations.length > 0, 'expected at least one violation for shell-only directory');
    assert(violations[0].includes('folder-only shell'), `expected shell violation, got: ${violations[0]}`);
  } finally {
    cleanFixture(root);
  }
});

test('valid package passes', () => {
  const root = createFixture({
    'platform/typescript/kernel-lifecycle/package.json': JSON.stringify({ name: '@ghatana/kernel-lifecycle' }),
    'platform/typescript/kernel-lifecycle/tsconfig.json': '{}',
    'platform/typescript/kernel-lifecycle/src/index.ts': 'export {};',
  });

  try {
    const { violations } = findOrphanModules({ repoRoot: root, allowlist: { allowlist: [] } });
    assert.equal(violations.length, 0, `expected no violations for valid package, got: ${violations.join(', ')}`);
  } finally {
    cleanFixture(root);
  }
});

test('missing package.json fails', () => {
  const root = createFixture({
    'platform/typescript/broken-module/tsconfig.json': '{}',
    'platform/typescript/broken-module/src/index.ts': 'export {};',
  });

  try {
    const { violations } = findOrphanModules({ repoRoot: root, allowlist: { allowlist: [] } });
    assert(violations.length > 0, 'expected violation for missing package.json');
    assert(violations[0].includes('package.json'), `expected package.json violation, got: ${violations[0]}`);
  } finally {
    cleanFixture(root);
  }
});

test('missing build.gradle.kts in Java module fails', () => {
  const root = createFixture({
    'platform/java/broken-module/src/main/java/Foo.java': 'class Foo {}',
  });

  try {
    const { violations } = findOrphanModules({ repoRoot: root, allowlist: { allowlist: [] } });
    assert(violations.length > 0, 'expected violation for missing build.gradle.kts');
    assert(violations[0].includes('build.gradle.kts'), `expected build.gradle.kts violation, got: ${violations[0]}`);
  } finally {
    cleanFixture(root);
  }
});

test('allowlisted path is skipped', () => {
  const root = createFixture({
    'platform/typescript/my-shell/index.ts': 'export {};',
  });

  try {
    const { violations } = findOrphanModules({
      repoRoot: root,
      allowlist: {
        allowlist: [
          {
            path: 'platform/typescript/my-shell',
            missingFile: 'package.json',
            reason: 'Legacy module pending migration',
            owner: 'Test',
            reviewBy: '2099-12-31',
          },
        ],
      },
    });
    assert.equal(violations.length, 0, 'expected no violations with allowlisted path');
  } finally {
    cleanFixture(root);
  }
});

test('parent directory with sub-packages is not flagged', () => {
  const root = createFixture({
    'platform/typescript/foundation/tokens/package.json': JSON.stringify({ name: '@ghatana/tokens' }),
    'platform/typescript/foundation/tokens/tsconfig.json': '{}',
    'platform/typescript/foundation/tokens/src/index.ts': 'export {};',
  });

  try {
    const { violations } = findOrphanModules({ repoRoot: root, allowlist: { allowlist: [] } });
    const foundationViolations = violations.filter((v) => v.includes('foundation'));
    assert.equal(foundationViolations.length, 0, `expected no violations for parent directory with sub-packages, got: ${foundationViolations.join(', ')}`);
  } finally {
    cleanFixture(root);
  }
});

test('orphans array contains correct metadata', () => {
  const root = createFixture({
    'platform/typescript/my-shell/index.ts': 'export {};',
  });

  try {
    const { orphans } = findOrphanModules({ repoRoot: root, allowlist: { allowlist: [] } });
    assert(orphans.length > 0, 'expected at least one orphan');
    assert.equal(orphans[0].todoId, 'P0-T08');
    assert.equal(orphans[0].owner, 'Platform Team');
    assert.equal(orphans[0].validationCommand, 'pnpm check:orphan-modules && pnpm check:cleanup-gate');
    assert.equal(orphans[0].type, 'typescript');
    assert.equal(orphans[0].reason, 'shell-only-directory');
    assert(orphans[0].action.includes('package.json') || orphans[0].action.includes('allowlist'));
    assert(orphans[0].path.includes('my-shell'));
  } finally {
    cleanFixture(root);
  }
});
