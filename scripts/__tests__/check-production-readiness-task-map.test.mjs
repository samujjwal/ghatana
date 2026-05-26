import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { createProductionReadinessTaskMapEvidence } from '../check-production-readiness-task-map.mjs';
import { renderProductionReadinessTaskMap } from '../generate-production-readiness-task-map.mjs';

function tempRoot() {
  const root = mkdtempSync(path.join(os.tmpdir(), 'ghatana-task-map-'));
  execFileSync('git', ['init'], { cwd: root, stdio: 'ignore' });
  execFileSync('git', ['config', 'user.email', 'test@example.com'], { cwd: root, stdio: 'ignore' });
  execFileSync('git', ['config', 'user.name', 'Test User'], { cwd: root, stdio: 'ignore' });
  writeFileSync(path.join(root, 'README.md'), 'fixture\n');
  execFileSync('git', ['add', 'README.md'], { cwd: root, stdio: 'ignore' });
  execFileSync('git', ['commit', '-m', 'fixture'], { cwd: root, stdio: 'ignore' });
  return root;
}

function write(root, relativePath, content) {
  const fullPath = path.join(root, relativePath);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, typeof content === 'string' ? content : JSON.stringify(content, null, 2));
}

function writeRequiredTestClaims(root) {
  const paths = [
    'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/TenantIsolationTest.java',
    'products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/EntityCrudContractTest.java',
    'products/data-cloud/planes/event/store/src/test/java/com/ghatana/datacloud/storage/EventLogContractTest.java',
    'products/data-cloud/planes/governance/core/src/test/java/com/ghatana/datacloud/governance/GovernancePolicyTest.java',
    'products/data-cloud/planes/governance/core/src/test/java/com/ghatana/datacloud/governance/audit/GovernanceAuditServiceTest.java',
    'products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/RuntimeTruthServiceTest.java',
    'products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/PatternSpecValidatorTest.java',
    'products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/PatternSpecCompilerTest.java',
    'products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/PatternSpecGoldenTests.java',
    'products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/operator/agent/EventOperatorCapabilityArchitectureContractTest.java',
  ];
  for (const relativePath of paths) {
    write(root, relativePath, 'placeholder');
  }
}

function writePackage(root) {
  write(root, 'package.json', {
    scripts: {
      'check:evidence-current-commit': 'node ./scripts/check-evidence-current-commit.mjs',
      'check:data-cloud-active-module-evidence': 'node ./scripts/generate-data-cloud-active-modules-evidence.mjs',
      'check:action-plane-boundaries': 'node ./scripts/check-action-plane-boundaries.mjs',
      'check:product-release-readiness': 'node ./scripts/check-product-release-readiness.mjs',
      'check:data-cloud-ai-governance-behavioral-proof': 'node ./scripts/check-ai-governance-behavioral-proof.mjs',
      'check:action-plane-module-inventory': 'node ./scripts/check-action-plane-module-inventory.mjs',
      'check:agent-capability-duplicates': 'node ./scripts/check-agent-capability-duplicates.mjs',
      'check:agent-runtime-test-excludes': 'node ./scripts/check-agent-runtime-test-excludes.mjs',
      'check:agent-usage-audit': 'node ./scripts/audit-agent-usage.mjs',
      'check:audit-completeness': 'node ./scripts/check-audit-completeness.mjs',
      'check:data-cloud-operations-readiness': 'node ./scripts/check-data-cloud-operations-readiness.mjs',
      'check:production-readiness-task-map': 'node ./scripts/check-production-readiness-task-map.mjs',
    },
  });
}

function writeReadiness(root, status = 'production-ready') {
  write(root, 'products/data-cloud/lifecycle/readiness-evidence.yaml', `status: ${status}\n`);
}

function taskMap(commit) {
  return [
    '# Data-Cloud Production Readiness Task Map',
    '',
    '**Current readiness state:** production-ready. Implementation checklist progress is not release truth.',
    '',
    'Readiness progresses through `blocked`, `candidate`, `staging-ready`, and `production-ready`. It must not jump directly from `blocked` to `production-ready`.',
    '',
    '| Task | Implementation Status | Evidence Status | Evidence Commit | Release Blocking | Verified At | Evidence File | Evidence Command |',
    '| --- | --- | --- | --- | --- | --- | --- | --- |',
    `| DC-P0-001 readiness blocked until proof passes | completed | verified | ${commit} | yes | 2026-05-26T00:00:00Z | \`.kernel/evidence/product-release-readiness.json\` | \`pnpm check:evidence-current-commit\` |`,
    '',
  ].join('\n');
}

function writeGeneratedTaskMapEvidence(root, commit) {
  const evidenceFiles = [
    '.kernel/evidence/product-release-readiness.json',
    '.kernel/evidence/data-cloud-active-modules.json',
    '.kernel/evidence/action-plane-boundaries.json',
    '.kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json',
    '.kernel/evidence/action-plane-module-inventory.json',
    '.kernel/evidence/agent-capability-duplicates.json',
    '.kernel/evidence/agent-runtime-test-excludes.json',
    '.kernel/evidence/agent-usage-audit.json',
    '.kernel/evidence/audit-completeness.json',
    '.kernel/evidence/data-cloud-operations-readiness.json',
    '.kernel/evidence/production-readiness-task-map.json',
  ];
  for (const evidenceFile of evidenceFiles) {
    write(root, evidenceFile, { evidenceRun: { commit } });
  }
}

test('rejects stale markdown Evidence Commit values even when referenced evidence is current', () => {
  const root = tempRoot();
  try {
    const head = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
    const stale = 'b'.repeat(40);
    writePackage(root);
    writeReadiness(root);
    writeRequiredTestClaims(root);
    write(root, 'products/data-cloud/docs/audits/PRODUCTION_READINESS_TASK_MAP.md', taskMap(stale));
    write(root, '.kernel/evidence/product-release-readiness.json', { evidenceRun: { commit: head } });

    const evidence = createProductionReadinessTaskMapEvidence(root, new Date('2026-05-26T00:00:00Z'));

    assert.equal(evidence.pass, false);
    assert.ok(evidence.violations.some((violation) => violation.includes(`Evidence Commit ${stale} must match HEAD ${head}`)));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects task map markdown that drifts from generated evidence table', () => {
  const root = tempRoot();
  try {
    const head = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
    writePackage(root);
    writeReadiness(root);
    writeRequiredTestClaims(root);
    writeGeneratedTaskMapEvidence(root, head);
    const generated = renderProductionReadinessTaskMap(root, new Date('2026-05-26T00:00:00Z'));
    write(root, 'products/data-cloud/docs/audits/PRODUCTION_READINESS_TASK_MAP.md', generated.replace('DC-P0-002 Action Plane boundary evidence', 'DC-P0-002 manually edited boundary evidence'));

    const evidence = createProductionReadinessTaskMapEvidence(root, new Date('2026-05-26T00:00:00Z'));

    assert.equal(evidence.pass, false);
    assert.ok(evidence.violations.some((violation) => violation.includes('must match scripts/generate-production-readiness-task-map.mjs output')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects task map readiness state that contradicts readiness evidence', () => {
  const root = tempRoot();
  try {
    const head = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
    writePackage(root);
    writeReadiness(root, 'staging-ready');
    writeRequiredTestClaims(root);
    write(root, 'products/data-cloud/docs/audits/PRODUCTION_READINESS_TASK_MAP.md', taskMap(head));
    write(root, '.kernel/evidence/product-release-readiness.json', { evidenceRun: { commit: head } });

    const evidence = createProductionReadinessTaskMapEvidence(root, new Date('2026-05-26T00:00:00Z'));

    assert.equal(evidence.pass, false);
    assert.ok(evidence.violations.some((violation) => violation.includes('must match readiness-evidence.yaml status staging-ready')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
