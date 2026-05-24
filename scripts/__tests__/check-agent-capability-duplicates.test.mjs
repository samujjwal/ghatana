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
