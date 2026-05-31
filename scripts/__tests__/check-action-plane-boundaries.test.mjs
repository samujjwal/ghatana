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
    assert.equal(evidence.scope.coLocatedActionRoot, 'products/data-cloud/planes/action');
    assert.deepEqual(evidence.scope.excludedRoots, ['products/data-cloud/planes/action']);
    assert.ok(evidence.violations.some((violation) => violation.rule === 'action-plane-gradle-dependency'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects EventCloud semantics from non-action Data Cloud planes', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/intelligence/feature-ingest/README.md', `
      # Feature ingest
      Reads directly from EventCloud and owns complex event processing.
    `);

    const violations = findActionPlaneBoundaryViolations(root).violations;

    assert.ok(violations.some((violation) => violation.rule === 'eventcloud-semantics-in-data-cloud-plane'));
    assert.ok(violations.some((violation) => violation.rule === 'patternspec-semantics-in-data-cloud-plane'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('allows boundary assertions and stable persistence SPI wording', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/event/store/src/test/java/com/example/EventLogContractTest.java', `
      package com.example;
      // EventLog does not expose PatternSpec semantics.
      class EventLogContractTest {}
    `);
    write(root, 'products/data-cloud/planes/shared-spi/src/main/java/com/example/EventLogStore.java', `
      package com.example;
      /** Storage SPI that AEP's EventCloud can use for persistence. */
      interface EventLogStore {}
    `);

    assert.deepEqual(findActionPlaneBoundaryViolations(root).violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('allows AEP internal imports in test files for ArchUnit rule assertions', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/data/entity/src/test/java/com/example/ArchUnitTest.java', `
      package com.example;
      import com.ghatana.aep.runtime.AepRuntime;
      import static com.ghatana.aep.ArchUnitRules.noAepInternalImports;
      class ArchUnitTest {}
    `);

    assert.deepEqual(findActionPlaneBoundaryViolations(root).violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('allows AEP internal imports in connector bridge implementations', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/extensions/plugins/trino/src/main/java/com/example/TrinoConnectorBridge.java', `
      package com.example;
      import com.ghatana.aep.runtime.AepRuntime;
      import com.ghatana.aep.connector.ConnectorBridge;
      class TrinoConnectorBridge {}
    `);

    assert.deepEqual(findActionPlaneBoundaryViolations(root).violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('allows runtime composition to depend on planes through public contracts', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/delivery/runtime-composition/src/main/java/com/example/RuntimeComposer.java', `
      package com.example;
      import com.ghatana.datacloud.planes.action.api.ActionPlaneApi;
      import com.ghatana.datacloud.planes.data.api.DataPlaneApi;
      class RuntimeComposer {}
    `);

    assert.deepEqual(findActionPlaneBoundaryViolations(root).violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('allows kernel bridge to use public contracts/SPI', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/extensions/kernel-bridge/src/main/java/com/example/KernelBridge.java', `
      package com.example;
      import com.ghatana.datacloud.shared.spi.EventLogStore;
      import com.ghatana.datacloud.contracts.ActionPlaneContract;
      class KernelBridge {}
    `);

    assert.deepEqual(findActionPlaneBoundaryViolations(root).violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects contracts importing runtime implementation packages', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/contracts/src/main/java/com/example/ActionPlaneContract.java', `
      package com.example;
      import com.ghatana.datacloud.planes.action.engine.ActionEngine;
      import com.ghatana.datacloud.planes.data.entity.DataEntity;
      class ActionPlaneContract {}
    `);

    const violations = findActionPlaneBoundaryViolations(root).violations;

    assert.ok(violations.some((violation) => violation.rule === 'contracts-no-implementation-dependency'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects PatternSpec semantics in non-action planes', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/data/entity/src/main/java/com/example/Entity.java', `
      package com.example;
      /** Entity for PatternSpec lifecycle management. */
      class Entity {}
    `);

    const violations = findActionPlaneBoundaryViolations(root).violations;

    assert.ok(violations.some((violation) => violation.rule === 'patternspec-semantics-in-data-cloud-plane'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects EventOperator semantics in non-action planes', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/intelligence/feature-ingest/src/main/java/com/example/Service.java', `
      package com.example;
      /** Service using EventOperatorCapability runtime. */
      class Service {}
    `);

    const violations = findActionPlaneBoundaryViolations(root).violations;

    assert.ok(violations.some((violation) => violation.rule === 'patternspec-semantics-in-data-cloud-plane'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects CEP semantics in non-action planes', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/governance/core/src/main/java/com/example/Policy.java', `
      package com.example;
      /** Policy for complex event processing. */
      class Policy {}
    `);

    const violations = findActionPlaneBoundaryViolations(root).violations;

    assert.ok(violations.some((violation) => violation.rule === 'patternspec-semantics-in-data-cloud-plane'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('allows EventCloud terminology in documentation explaining boundaries', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/event/store/README.md', `
      # Event Store
      This plane does not expose EventCloud semantics.
      EventCloud is owned by AEP and uses this plane for persistence.
    `);

    assert.deepEqual(findActionPlaneBoundaryViolations(root).violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('excludes Action Plane implementation directories from boundary checks', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/action/engine/src/main/java/com/example/AepRuntime.java', `
      package com.example;
      import com.ghatana.aep.runtime.AepRuntime;
      import com.ghatana.aep.connector.ConnectorBridge;
      class AepRuntime {}
    `);
    write(root, 'products/data-cloud/planes/action/orchestrator/src/main/java/com/example/Orchestrator.java', `
      package com.example;
      import com.ghatana.aep.orchestrator.Orchestrator;
      class Orchestrator {}
    `);

    assert.deepEqual(findActionPlaneBoundaryViolations(root).violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
