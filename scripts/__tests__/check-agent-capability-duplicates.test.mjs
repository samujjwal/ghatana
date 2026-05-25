import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { findAgentCapabilityDuplicateViolations } from '../check-agent-capability-duplicates.mjs';

function tempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-agent-capability-duplicates-'));
}

function write(root, relativePath, source) {
  const fullPath = path.join(root, relativePath);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, source);
}

test('passes capability model source without old AgentOperator surfaces', () => {
  const root = tempRepo();
  try {
    write(root, 'src/main/java/com/example/EventOperatorCapability.java', `
      interface EventOperatorCapability<I, O> extends AgentCapability<I, O> {}
      final class AgentEventOperatorCapabilityAdapter {}
    `);

    const violations = findAgentCapabilityDuplicateViolations(root, ['src/main/java']);

    assert.deepEqual(violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects old AgentOperator inheritance and adapter surfaces', () => {
  const root = tempRepo();
  try {
    write(root, 'src/main/java/com/example/AgentOperator.java', `
      import com.ghatana.core.operator.agent.AgentOperator;
      interface AgentOperator<I, O> extends EventOperator<I, O> {}
      final class AgentOperatorAdapter {}
    `);

    const violations = findAgentCapabilityDuplicateViolations(root, ['src/main/java']);

    assert.ok(violations.some((violation) => violation.rule === 'agent-operator-interface'));
    assert.ok(violations.some((violation) => violation.rule === 'agent-operator-import'));
    assert.ok(violations.some((violation) => violation.rule === 'agent-operator-adapter'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects PatternSpec helper names that preserve the old model', () => {
  const root = tempRepo();
  try {
    write(root, 'src/main/java/com/example/PatternRuntimeNode.java', `
      record PatternRuntimeNode() {
        boolean isAgentOperator() { return true; }
      }
    `);

    const violations = findAgentCapabilityDuplicateViolations(root, ['src/main/java']);

    assert.equal(violations.length, 1);
    assert.equal(violations[0].rule, 'pattern-agent-operator-helper');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('requires issue and removal deadline for temporary process(Event) compatibility', () => {
  const root = tempRepo();
  try {
    write(root, 'src/main/java/com/example/AgentEventOperatorCapabilityAdapter.java', `
      final class AgentEventOperatorCapabilityAdapter {
        Promise<OperatorResult> process(Event event) { return null; }
      }
    `);

    const violations = findAgentCapabilityDuplicateViolations(root, ['src/main/java']);

    assert.ok(violations.some((violation) => violation.rule === 'agent-event-process-compatibility-missing-tracker'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('fails temporary process(Event) compatibility after the migration deadline', () => {
  const root = tempRepo();
  try {
    write(root, 'src/main/java/com/example/AgentEventOperatorCapabilityAdapter.java', `
      final class AgentEventOperatorCapabilityAdapter {
        // AEP-AGENT-CAP-001 remove by 2026-06-30.
        Promise<OperatorResult> process(Event event) { return null; }
      }
    `);

    const violations = findAgentCapabilityDuplicateViolations(root, ['src/main/java'], {
      now: new Date('2026-07-01T00:00:00.000Z'),
    });

    assert.ok(violations.some((violation) => violation.rule === 'agent-event-process-compatibility-expired'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects unsafe production defaults in agent capability adapter', () => {
  const root = tempRepo();
  try {
    write(root, 'src/main/java/com/example/AgentEventOperatorCapabilityAdapter.java', `
      final class AgentEventOperatorCapabilityAdapter {
        OperatorType getType() { return OperatorType.AGENT; }
        MemoryStore memoryStore() { return MemoryStore.noOp(); }
        Map<String, Object> getMetrics() { return Map.of(); }
        Object tenant() { return "default-tenant"; }
        Object value() { return null; }
      }
    `);

    const violations = findAgentCapabilityDuplicateViolations(root, ['src/main/java']);

    assert.ok(violations.some((violation) => violation.rule === 'agent-capability-memory-noop'));
    assert.ok(violations.some((violation) => violation.rule === 'agent-capability-default-tenant'));
    assert.ok(violations.some((violation) => violation.rule === 'agent-capability-empty-metrics'));
    assert.ok(violations.some((violation) => violation.rule === 'agent-capability-return-null'));
    assert.ok(violations.some((violation) => violation.rule === 'agent-capability-operator-type-agent'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
