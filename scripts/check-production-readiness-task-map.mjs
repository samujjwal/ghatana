#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { renderProductionReadinessTaskMap } from './generate-production-readiness-task-map.mjs';

const SCRIPT_PATH = 'scripts/check-production-readiness-task-map.mjs';
const TASK_MAP_PATH = 'products/data-cloud/docs/audits/PRODUCTION_READINESS_TASK_MAP.md';
const READINESS_PATH = 'products/data-cloud/lifecycle/readiness-evidence.yaml';
const EVIDENCE_PATH = '.kernel/evidence/production-readiness-task-map.json';
const COMMAND = 'pnpm check:production-readiness-task-map';
const COMMIT_PATTERN = /^[a-f0-9]{40}$/i;
const REQUIRED_COLUMNS = [
  'Task',
  'Implementation Status',
  'Evidence Status',
  'Evidence Commit',
  'Release Blocking',
  'Verified At',
  'Evidence File',
  'Evidence Command',
];
const REQUIRED_TEST_CLAIMS = [
  {
    name: 'TenantIsolationTest',
    patterns: [
      'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/TenantIsolationTest.java',
      'products/data-cloud/delivery/runtime-composition/src/test/java/com/ghatana/datacloud/security/DataCloudTenantIsolationTest.java',
      'products/data-cloud/planes/action/engine/src/test/java/com/ghatana/aep/engine/TenantIsolationTest.java',
    ],
  },
  {
    name: 'EntityCrudContractTest',
    patterns: [
      'products/data-cloud/planes/data/entity/src/test/java/com/ghatana/datacloud/entity/EntityCrudContractTest.java',
    ],
  },
  {
    name: 'EventLogContractTest',
    patterns: [
      'products/data-cloud/planes/event/store/src/test/java/com/ghatana/datacloud/storage/EventLogContractTest.java',
    ],
  },
  {
    name: 'GovernancePolicyTest',
    patterns: [
      'products/data-cloud/planes/governance/core/src/test/java/com/ghatana/datacloud/governance/GovernancePolicyTest.java',
    ],
  },
  {
    name: 'GovernanceAuditServiceTest',
    patterns: [
      'products/data-cloud/planes/governance/core/src/test/java/com/ghatana/datacloud/governance/audit/GovernanceAuditServiceTest.java',
    ],
  },
  {
    name: 'RuntimeTruthServiceTest',
    patterns: [
      'products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/RuntimeTruthServiceTest.java',
    ],
  },
  {
    name: 'PatternSpecValidatorTest',
    patterns: [
      'products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/PatternSpecValidatorTest.java',
    ],
  },
  {
    name: 'PatternSpecCompilerTest',
    patterns: [
      'products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/PatternSpecCompilerTest.java',
    ],
  },
  {
    name: 'PatternSpecGoldenTests',
    patterns: [
      'products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/pattern/spec/PatternSpecGoldenTests.java',
    ],
  },
  {
    name: 'EventOperatorCapabilityArchitectureContractTest',
    patterns: [
      'products/data-cloud/planes/action/operator-contracts/src/test/java/com/ghatana/aep/operator/agent/EventOperatorCapabilityArchitectureContractTest.java',
    ],
  },
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

function parseTable(markdown) {
  const rows = [];
  let headers = null;
  for (const line of markdown.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('|') || !trimmed.endsWith('|')) {
      continue;
    }
    const cells = trimmed.slice(1, -1).split('|').map((cell) => cell.trim());
    if (!headers && cells.includes('Task')) {
      headers = cells;
      continue;
    }
    if (!headers || cells.every((cell) => /^:?-{3,}:?$/.test(cell))) {
      continue;
    }
    if (cells.length === headers.length) {
      rows.push(Object.fromEntries(headers.map((header, index) => [header, cells[index]])));
    }
  }
  return { headers: headers ?? [], rows };
}

function cleanPath(value) {
  return value.replaceAll('`', '').trim();
}

function packageScripts(root) {
  const packageJsonPath = path.join(root, 'package.json');
  if (!existsSync(packageJsonPath)) {
    return {};
  }
  return JSON.parse(readFileSync(packageJsonPath, 'utf8')).scripts ?? {};
}

function evidenceCommit(root, evidencePath) {
  const fullPath = path.join(root, evidencePath);
  if (!existsSync(fullPath) || path.extname(fullPath) !== '.json') {
    return null;
  }
  try {
    return JSON.parse(readFileSync(fullPath, 'utf8'))?.evidenceRun?.commit ?? null;
  } catch {
    return null;
  }
}

function readinessStatus(readiness) {
  return readiness.match(/^status:\s*([a-z-]+)\s*$/m)?.[1] ?? null;
}

function normalizedCellCommit(value) {
  const cleaned = cleanPath(String(value ?? '')).trim();
  return COMMIT_PATTERN.test(cleaned) ? cleaned : null;
}

function normalizeGeneratedTaskMap(markdown) {
  return markdown
    .replace(/\|\s*\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z\s*\|/g, '| <verifiedAt> |')
    .trim();
}

export function createProductionReadinessTaskMapEvidence(root = process.cwd(), now = new Date()) {
  const violations = [];
  const taskMapFullPath = path.join(root, TASK_MAP_PATH);
  const readinessFullPath = path.join(root, READINESS_PATH);
  const head = currentGitSha(root);
  const targetCommitSha = process.env.TARGET_COMMIT_SHA ?? process.env.AUDIT_TARGET_COMMIT ?? head;
  const targetEnvironment = process.env.RELEASE_ENVIRONMENT ?? 'staging';
  const scripts = packageScripts(root);

  if (!existsSync(taskMapFullPath)) {
    violations.push(`${TASK_MAP_PATH} is missing`);
  }
  if (!existsSync(readinessFullPath)) {
    violations.push(`${READINESS_PATH} is missing`);
  }

  const markdown = existsSync(taskMapFullPath) ? readFileSync(taskMapFullPath, 'utf8') : '';
  const readiness = existsSync(readinessFullPath) ? readFileSync(readinessFullPath, 'utf8') : '';
  const { headers, rows } = parseTable(markdown);
  const status = readinessStatus(readiness);

  if (markdown && normalizeGeneratedTaskMap(markdown) !== normalizeGeneratedTaskMap(renderProductionReadinessTaskMap(root, now))) {
    violations.push(`${TASK_MAP_PATH} must match scripts/generate-production-readiness-task-map.mjs output`);
  }

  for (const column of REQUIRED_COLUMNS) {
    if (!headers.includes(column)) {
      violations.push(`Task map table is missing required column "${column}"`);
    }
  }

  if (/ready for production deployment/i.test(markdown) && status === 'blocked') {
    violations.push('Task map must not claim production readiness while readiness-evidence.yaml is blocked');
  }
  const stateClaim = markdown.match(/\*\*Current readiness state:\*\*\s*([a-z-]+)/i)?.[1];
  if (stateClaim && status && stateClaim !== status) {
    violations.push(`Task map current readiness state ${stateClaim} must match readiness-evidence.yaml status ${status}`);
  }
  if (!/\bblocked\b[\s\S]*\bcandidate\b[\s\S]*\bstaging-ready\b[\s\S]*\bproduction-ready\b/.test(markdown)) {
    violations.push('Task map must document blocked -> candidate -> staging-ready -> production-ready progression');
  }

  for (const row of rows) {
    const task = row.Task ?? 'unknown task';
    const evidenceFile = cleanPath(row['Evidence File'] ?? '');
    const evidenceCommand = cleanPath(row['Evidence Command'] ?? '');
    const releaseBlocking = (row['Release Blocking'] ?? '').toLowerCase();
    const evidenceStatus = (row['Evidence Status'] ?? '').toLowerCase();
    const evidenceCommitValue = (row['Evidence Commit'] ?? '').toLowerCase();
    const rowCommit = normalizedCellCommit(row['Evidence Commit']);

    if (!['yes', 'no'].includes(releaseBlocking)) {
      violations.push(`${task}: Release Blocking must be yes/no`);
    }
    if (releaseBlocking === 'yes' && evidenceStatus !== 'verified') {
      violations.push(`${task}: release-blocking evidence must be verified, got ${row['Evidence Status'] ?? 'missing'}`);
    }
    if (releaseBlocking === 'yes' && evidenceCommitValue.includes('current head required')) {
      violations.push(`${task}: release-blocking evidence must be generated at current HEAD ${head}`);
    }
    if (releaseBlocking === 'yes' && !rowCommit) {
      violations.push(`${task}: release-blocking Evidence Commit must be a 40-character git SHA`);
    }
    if (rowCommit && rowCommit !== head) {
      violations.push(`${task}: Evidence Commit ${rowCommit} must match HEAD ${head}`);
    }
    if (evidenceFile && evidenceFile !== EVIDENCE_PATH && !existsSync(path.join(root, evidenceFile))) {
      violations.push(`${task}: evidence file/path does not exist: ${evidenceFile}`);
    }
    if (evidenceCommand.startsWith('pnpm ')) {
      const scriptName = evidenceCommand.replace(/^pnpm\s+/, '').split(/\s+/)[0];
      if (!scripts[scriptName]) {
        violations.push(`${task}: package script is missing for ${scriptName}`);
      }
    }
    const commit = evidenceCommit(root, evidenceFile);
    if (evidenceStatus === 'verified' && commit !== head) {
      violations.push(`${task}: cannot be verified because evidence commit ${commit ?? 'missing'} does not match HEAD ${head}`);
    }
    if (rowCommit && commit && rowCommit !== commit) {
      violations.push(`${task}: Evidence Commit ${rowCommit} must match ${evidenceFile} evidenceRun.commit ${commit}`);
    }
  }

  const verifiedTestClaims = [];
  for (const claim of REQUIRED_TEST_CLAIMS) {
    const matches = claim.patterns.filter((candidate) => existsSync(path.join(root, candidate)));
    if (matches.length === 0) {
      violations.push(`${claim.name}: claimed release test file is missing`);
    }
    verifiedTestClaims.push({
      name: claim.name,
      pass: matches.length > 0,
      paths: matches,
    });
  }

  return {
    generatedAt: now.toISOString(),
    pass: violations.length === 0,
    evidenceRun: {
      generatedBy: SCRIPT_PATH,
      source: SCRIPT_PATH,
      command: COMMAND,
      commit: head,
      sourceCommitSha: head,
      targetCommitSha,
      targetEnvironment,
    },
    sourceCommitSha: head,
    targetCommitSha,
    targetEnvironment,
    validationStatus: violations.length === 0 ? 'validated' : 'failed',
    reviewDueAt: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
    expiresAt: new Date(now.getTime() + 48 * 60 * 60 * 1000).toISOString(),
    source: {
      taskMap: TASK_MAP_PATH,
      readiness: READINESS_PATH,
      packageScripts: 'package.json',
    },
    summary: {
      taskRows: rows.length,
      releaseBlockingRows: rows.filter((row) => (row['Release Blocking'] ?? '').toLowerCase() === 'yes').length,
      requiredTestClaims: REQUIRED_TEST_CLAIMS.length,
      verifiedTestClaims: verifiedTestClaims.filter((claim) => claim.pass).length,
      violationCount: violations.length,
    },
    tasks: rows,
    requiredTestClaims: verifiedTestClaims,
    violations,
  };
}

export function writeProductionReadinessTaskMapEvidence(root = process.cwd(), evidence = createProductionReadinessTaskMapEvidence(root)) {
  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const evidence = createProductionReadinessTaskMapEvidence(root);
  writeProductionReadinessTaskMapEvidence(root, evidence);

  if (!evidence.pass) {
    console.error('Production readiness task map check failed:\n');
    for (const violation of evidence.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log(`Production readiness task map evidence written to ${EVIDENCE_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
