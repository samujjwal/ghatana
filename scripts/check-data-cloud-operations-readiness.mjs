#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const SCRIPT_PATH = 'scripts/check-data-cloud-operations-readiness.mjs';
const EVIDENCE_PATH = '.kernel/evidence/data-cloud-operations-readiness.json';
const COMMAND = 'pnpm check:data-cloud-operations-readiness';

const REQUIRED_FILES = [
  'products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/RuntimeTruthService.java',
  'products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/PlaneHealthChecker.java',
  'products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/BackupRestoreService.java',
  'products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/RuntimeTruthServiceTest.java',
  'products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/PlaneHealthCheckerTest.java',
  'products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/BackupRestoreServiceTest.java',
  'products/data-cloud/docs/operations/README.md',
  'products/data-cloud/docs/operations/RUNBOOK.md',
  'products/data-cloud/docs/operations/PRODUCTION_PROFILE_CHECKLIST.md',
  'products/data-cloud/deploy/helm/data-cloud/Chart.yaml',
  'products/data-cloud/deploy/k8s/deployment.yaml',
  'release-evidence/backup/backup-drill-report.json',
  '.kernel/evidence/product-slo-budgets.json',
  '.kernel/evidence/product-cost-budgets.json',
  '.kernel/evidence/data-cloud-release-runtime-profile.json',
];

const REQUIRED_SOURCE_TOKENS = [
  {
    file: 'products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/RuntimeTruthServiceTest.java',
    tokens: ['live/degraded/unavailable', 'dependencies', 'health snapshots', 'tenant scoping', 'provenance refs', 'artifact refs', 'Failure Injection Tests', 'Degraded Dependency Tests'],
  },
  {
    file: 'products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/PlaneHealthCheckerTest.java',
    tokens: ['Plane Health Checks', 'System Health Aggregation', 'returns unhealthy for DOWN plane'],
  },
  {
    file: 'products/data-cloud/planes/operations/config/src/test/java/com/ghatana/datacloud/config/BackupRestoreServiceTest.java',
    tokens: ['Backup Creation', 'Restore Operations', 'Backup Management'],
  },
  {
    file: 'products/data-cloud/docs/operations/RUNBOOK.md',
    tokens: ['backup', 'restore', 'health'],
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

function readJson(root, relativePath) {
  try {
    return JSON.parse(readFileSync(path.join(root, relativePath), 'utf8'));
  } catch {
    return null;
  }
}

export function createDataCloudOperationsReadinessEvidence(root = process.cwd(), now = new Date()) {
  const head = currentGitSha(root);
  const violations = [];
  const presentFiles = [];

  for (const file of REQUIRED_FILES) {
    if (!existsSync(path.join(root, file))) {
      violations.push(`missing required operations readiness file: ${file}`);
    } else {
      presentFiles.push(file);
    }
  }

  for (const requirement of REQUIRED_SOURCE_TOKENS) {
    const fullPath = path.join(root, requirement.file);
    if (!existsSync(fullPath)) {
      continue;
    }
    const source = readFileSync(fullPath, 'utf8');
    for (const token of requirement.tokens) {
      if (!source.toLowerCase().includes(token.toLowerCase())) {
        violations.push(`${requirement.file}: missing operations readiness token ${JSON.stringify(token)}`);
      }
    }
  }

  const backup = readJson(root, 'release-evidence/backup/backup-drill-report.json');
  if (backup && backup.fail !== 0) {
    violations.push('release-evidence/backup/backup-drill-report.json: backup drill fail count must be 0');
  }
  for (const evidencePath of [
    '.kernel/evidence/product-slo-budgets.json',
    '.kernel/evidence/product-cost-budgets.json',
    '.kernel/evidence/data-cloud-release-runtime-profile.json',
  ]) {
    const evidence = readJson(root, evidencePath);
    if (evidence?.evidenceRun?.commit && evidence.evidenceRun.commit !== head) {
      violations.push(`${evidencePath}: evidenceRun.commit ${evidence.evidenceRun.commit} must match HEAD ${head}`);
    }
    if (evidence?.pass === false || evidence?.status === 'failed') {
      violations.push(`${evidencePath}: evidence must pass`);
    }
  }

  return {
    generatedAt: now.toISOString(),
    pass: violations.length === 0,
    evidenceRun: {
      generatedBy: SCRIPT_PATH,
      source: SCRIPT_PATH,
      command: COMMAND,
      commit: head,
    },
    summary: {
      requiredFileCount: REQUIRED_FILES.length,
      presentFileCount: presentFiles.length,
      readinessAreaCount: 7,
      violationCount: violations.length,
    },
    readinessAreas: [
      'runtime truth',
      'health checks',
      'backup/restore drill',
      'SLO budget',
      'cost budget',
      'alerting',
      'deployment manifests and Helm/k8s render inputs',
    ],
    presentFiles,
    violations,
  };
}

export function writeDataCloudOperationsReadinessEvidence(root = process.cwd(), evidence = createDataCloudOperationsReadinessEvidence(root)) {
  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const evidence = createDataCloudOperationsReadinessEvidence(root);
  writeDataCloudOperationsReadinessEvidence(root, evidence);

  if (!evidence.pass) {
    console.error('Data Cloud operations readiness check failed:\n');
    for (const violation of evidence.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log(`Data Cloud operations readiness evidence written to ${EVIDENCE_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
