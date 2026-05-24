import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { checkAgentRuntimeTestExcludes } from '../check-agent-runtime-test-excludes.mjs';

function tempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-agent-runtime-excludes-'));
}

function writeGradle(root, source) {
  const gradlePath = path.join(root, 'module', 'build.gradle.kts');
  mkdirSync(path.dirname(gradlePath), { recursive: true });
  writeFileSync(gradlePath, source);
  return 'module/build.gradle.kts';
}

test('passes exclusions with issue reference and target removal date', () => {
  const root = tempRepo();
  try {
    const gradleFile = writeGradle(root, `
      // GH-48210 / remove by 2026-07-15: ActiveJ harness migration.
      tasks.compileTestJava {
        exclude("**/RegistryAndFactoryTest.java")
      }
    `);

    assert.deepEqual(checkAgentRuntimeTestExcludes(root, gradleFile), []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects undocumented test exclusions', () => {
  const root = tempRepo();
  try {
    const gradleFile = writeGradle(root, `
      tasks.compileTestJava {
        exclude("**/HiddenTest.java")
      }
    `);

    const violations = checkAgentRuntimeTestExcludes(root, gradleFile);

    assert.equal(violations.length, 2);
    assert.ok(violations.some((violation) => violation.message.includes('issue reference')));
    assert.ok(violations.some((violation) => violation.message.includes('target removal date')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects excluding canonical capability factory tests', () => {
  const root = tempRepo();
  try {
    const gradleFile = writeGradle(root, `
      // GH-48210 / remove by 2026-07-15: ActiveJ harness migration.
      tasks.compileTestJava {
        exclude("**/AgentOperatorFactoryCanonicalTypeTest.java")
      }
    `);

    const violations = checkAgentRuntimeTestExcludes(root, gradleFile);

    assert.equal(violations.length, 1);
    assert.match(violations[0].message, /must not be excluded/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
