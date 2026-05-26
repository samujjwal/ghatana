import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { findDirectAgentUsage } from '../audit-agent-usage.mjs';

function tempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-agent-usage-audit-'));
}

function write(root, relativePath, content) {
  const fullPath = path.join(root, relativePath);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, content);
}

function writeRegistry(root, rows) {
  write(root, 'products/data-cloud/planes/action/agent-runtime/docs/AGENT_USAGE_EXCEPTIONS.md', [
    '# Agent Usage Exceptions',
    '',
    '| Surface | Scope | Owner | Review/Rationale | Revalidation |',
    '| --- | --- | --- | --- | --- |',
    ...rows,
    '',
  ].join('\n'));
}

test('agent usage audit requires explicit owner and revalidation metadata for exceptions', () => {
  const root = tempRepo();
  try {
    write(root, 'products/data-cloud/planes/action/agent-runtime/docs/AGENT_USAGE_EXCEPTIONS.md', [
      '# Agent Usage Exceptions',
      '',
      '| Surface | Scope | Reason |',
      '| --- | --- | --- |',
      '| `AgentCapabilityExecutionFactory` | Factory internals only | Builds governed trees. |',
      '',
    ].join('\n'));

    assert.throws(
      () => findDirectAgentUsage(root),
      /Surface, Scope, Owner, Review\/Rationale, and Revalidation/,
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('agent usage audit permits only registry-approved internals and test fixtures', () => {
  const root = tempRepo();
  try {
    writeRegistry(root, [
      '| `AgentCapabilityExecutionFactory` | Factory internals only | Data Cloud Action Plane | Builds governed trees. | Revalidate before release. |',
      '| test fixtures | test source only | Data Cloud Test Owners | Deterministic tests. | Revalidate when paths change. |',
    ]);
    write(
      root,
      'products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/AgentCapabilityExecutionFactory.java',
      'class AgentCapabilityExecutionFactory { void run(TypedAgent<?> agent) { agent.execute(null); } }',
    );
    write(
      root,
      'products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/UnsafeAgentEntryPoint.java',
      'class UnsafeAgentEntryPoint { void run(TypedAgent<?> agent) { agent.execute(null); } }',
    );

    const findings = findDirectAgentUsage(root);

    assert.equal(findings.length, 1);
    assert.equal(
      findings[0].file,
      'products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/UnsafeAgentEntryPoint.java',
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
