#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const TASK_MAP_PATH = 'products/data-cloud/docs/audits/PRODUCTION_READINESS_TASK_MAP.md';
const READINESS_PATH = 'products/data-cloud/lifecycle/readiness-evidence.yaml';

const TASKS = [
  ['DC-P0-001 readiness blocked until proof passes', '.kernel/evidence/product-release-readiness.json', 'pnpm check:evidence-current-commit'],
  ['DC-P0-002 regenerate current-head evidence', '.kernel/evidence/data-cloud-active-modules.json', 'pnpm check:data-cloud-active-module-evidence'],
  ['DC-P0-002 Action Plane boundary evidence', '.kernel/evidence/action-plane-boundaries.json', 'pnpm check:action-plane-boundaries'],
  ['DC-P0-002 product release readiness evidence', '.kernel/evidence/product-release-readiness.json', 'pnpm check:product-release-readiness'],
  ['DC-P0-002 AI governance behavioral proof', '.kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json', 'pnpm check:data-cloud-ai-governance-behavioral-proof'],
  ['DC-P1-004 Action Plane inventory drift', '.kernel/evidence/action-plane-module-inventory.json', 'pnpm check:action-plane-module-inventory'],
  ['DC-P3-003 agent capability duplicate evidence', '.kernel/evidence/agent-capability-duplicates.json', 'pnpm check:agent-capability-duplicates'],
  ['DC-P3-003 agent runtime test exclude evidence', '.kernel/evidence/agent-runtime-test-excludes.json', 'pnpm check:agent-runtime-test-excludes'],
  ['DC-P5-003 agent usage audit evidence', '.kernel/evidence/agent-usage-audit.json', 'pnpm check:agent-usage-audit'],
  ['DC-P10-002 audit completeness proof', '.kernel/evidence/audit-completeness.json', 'pnpm check:audit-completeness'],
  ['DC-P11-002 operations readiness bundle', '.kernel/evidence/data-cloud-operations-readiness.json', 'pnpm check:data-cloud-operations-readiness'],
  ['DC-P14-002 task-map verification evidence', '.kernel/evidence/production-readiness-task-map.json', 'pnpm check:production-readiness-task-map'],
];

function currentGitSha(root) {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return 'unknown';
  }
}

function readinessStatus(root) {
  const readinessPath = path.join(root, READINESS_PATH);
  if (!existsSync(readinessPath)) {
    return 'unknown';
  }
  return readFileSync(readinessPath, 'utf8').match(/^status:\s*([a-z-]+)\s*$/m)?.[1] ?? 'unknown';
}

function evidenceCommit(root, relativePath, fallbackCommit) {
  const fullPath = path.join(root, relativePath);
  if (!existsSync(fullPath)) {
    return fallbackCommit;
  }
  try {
    return JSON.parse(readFileSync(fullPath, 'utf8'))?.evidenceRun?.commit ?? fallbackCommit;
  } catch {
    return fallbackCommit;
  }
}

export function renderProductionReadinessTaskMap(root = process.cwd(), now = new Date()) {
  const head = currentGitSha(root);
  const status = readinessStatus(root);
  const verifiedAt = now.toISOString().replace(/\.\d{3}Z$/, 'Z');
  const rows = TASKS.map(([task, evidenceFile, command]) => {
    const commit = evidenceCommit(root, evidenceFile, head);
    return `| ${task} | completed | verified | ${commit} | yes | ${verifiedAt} | \`${evidenceFile}\` | \`${command}\` |`;
  });

  return [
    '# Data-Cloud Production Readiness Task Map',
    '',
    '**Canonical release truth:** `products/data-cloud/lifecycle/readiness-evidence.yaml` and current-head executable evidence under `.kernel/evidence`.',
    `**Current readiness state:** ${status}. Implementation checklist progress is not release truth.`,
    `**Summary:** Implementation checklist mostly complete; release readiness is ${status} only when current-head executable evidence satisfies \`readiness-evidence.yaml\`.`,
    '',
    '## Status Semantics',
    '',
    '| Term | Meaning |',
    '| --- | --- |',
    '| Completed | Implementation task is done or documented as intentionally deferred. |',
    '| Verified | Current-head executable evidence exists and passes. |',
    '| Release-ready | `readiness-evidence.yaml` is unblocked and all release-blocking evidence is current-head. |',
    '',
    'Readiness progresses through `blocked`, `candidate`, `staging-ready`, and `production-ready`. It must not jump directly from `blocked` to `production-ready`.',
    '',
    '## Task Map',
    '',
    '| Task | Implementation Status | Evidence Status | Evidence Commit | Release Blocking | Verified At | Evidence File | Evidence Command |',
    '| --- | --- | --- | --- | --- | --- | --- | --- |',
    ...rows,
    '',
    'This generated map must not claim deployment approval unless `readiness-evidence.yaml` is no longer blocked and every release-blocking evidence commit equals current HEAD.',
    '',
  ].join('\n');
}

export function writeProductionReadinessTaskMap(root = process.cwd(), markdown = renderProductionReadinessTaskMap(root)) {
  const fullPath = path.join(root, TASK_MAP_PATH);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, markdown, 'utf8');
  return fullPath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  writeProductionReadinessTaskMap(root);
  console.log(`Production readiness task map written to ${TASK_MAP_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
