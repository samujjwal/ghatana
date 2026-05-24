import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  createActionPlaneBoundaryEvidence,
  findActionPlaneBoundaryViolations,
} from '../check-action-plane-boundaries.mjs';

function tempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-action-plane-boundary-'));
}

function write(root, relativePath, source) {
  const fullPath = path.join(root, relativePath);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, source);
}

test('passes non-action Data Cloud planes without AEP internal imports', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/data/entity/src/main/java/com/example/Entity.java', `
      package com.example;
      import com.ghatana.datacloud.shared.SharedThing;
    `);
    write(root, 'products/data-cloud/planes/action/engine/src/main/java/com/example/AepRuntime.java', `
      package com.example;
      import com.ghatana.aep.runtime.AepRuntime;
    `);

    assert.deepEqual(findActionPlaneBoundaryViolations(root).violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects AEP internal imports from non-action Data Cloud planes', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/data/entity/src/main/java/com/example/Entity.java', `
      package com.example;
      import com.ghatana.aep.runtime.AepRuntime;
    `);

    const violations = findActionPlaneBoundaryViolations(root).violations;

    assert.equal(violations.length, 1);
    assert.equal(violations[0].rule, 'aep-internal-package');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects Action Plane Gradle dependencies from non-action Data Cloud planes', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/governance/core/build.gradle.kts', `
      dependencies {
        implementation(project(":products:data-cloud:planes:action:engine"))
      }
    `);

    const evidence = createActionPlaneBoundaryEvidence(root, new Date('2026-05-24T00:00:00.000Z'));

    assert.equal(evidence.pass, false);
    assert.ok(evidence.violations.some((violation) => violation.rule === 'action-plane-gradle-dependency'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
