#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const releaseWorkflowPath = path.join(repoRoot, '.github/workflows/data-cloud-release.yml');
const failureEvidenceDir = path.join(repoRoot, '.kernel/evidence/runtime-dependency-failure-injection');
const reportPrefix = 'runtime-dependency-failure-injection-';

const requiredFiles = [
  'products/data-cloud/scripts/run-smoke-e2e.sh',
  'products/data-cloud/scripts/run-backup-drill.sh',
  'products/data-cloud/scripts/run-durable-load-suite.sh',
  'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistryInvariantTest.java',
];

const requiredReleaseTokens = [
  'name: Data Cloud Release Gate',
  'smoke-e2e-strict',
  'backup-drill-strict',
  'security-scan-strict',
  "jq -e '.summary.fail == 0 and .summary.warn == 0' smoke-e2e-report.json >/dev/null",
  "jq -e '.fail == 0' backup-drill-report.json >/dev/null",
  'data-cloud-smoke-evidence',
  'data-cloud-backup-evidence',
];

const requiredCiTokens = [
  'Durable Load Suite (DC-P1-445)',
  'run-durable-load-suite.sh',
];

const requiredScenarioEvidence = [
  { key: 'postgresDown', patterns: ['postgres unavailability'] },
  { key: 'clickhouseDown', patterns: ['clickhouse unavailability'] },
  { key: 'openSearchDown', patterns: ['opensearch unavailability'] },
  { key: 's3Down', patterns: ['s3 unavailability'] },
  { key: 'auditSinkUnavailable', patterns: ['audit sink unavailability'] },
  { key: 'policyEngineUnavailable', patterns: ['policy engine unavailability'] },
  { key: 'aiCompletionUnavailable', patterns: ['ai completion unavailability'] },
  { key: 'networkTimeout', patterns: ['network timeout'] },
  { key: 'queueSaturation', patterns: ['queue saturation'] },
  { key: 'retryBackoff', patterns: ['retry implementation', 'backoff implementation'] },
];

const violations = [];

function findLatestReportFile() {
  if (!existsSync(failureEvidenceDir)) {
    return null;
  }

  const candidates = readdirSync(failureEvidenceDir)
    .filter((entry) => entry.startsWith(reportPrefix) && entry.endsWith('.json'))
    .map((entry) => ({
      entry,
      absolutePath: path.join(failureEvidenceDir, entry),
      modifiedAt: statSync(path.join(failureEvidenceDir, entry)).mtimeMs,
    }))
    .sort((left, right) => right.modifiedAt - left.modifiedAt);

  return candidates[0] ?? null;
}

function validateFailureInjectionReport() {
  const latestReport = findLatestReportFile();
  if (!latestReport) {
    violations.push('Missing runtime dependency failure-injection evidence report in .kernel/evidence/runtime-dependency-failure-injection');
    return;
  }

  let report;
  try {
    report = JSON.parse(readFileSync(latestReport.absolutePath, 'utf8'));
  } catch (error) {
    violations.push(`Invalid runtime dependency failure-injection report JSON: ${latestReport.entry} (${error.message})`);
    return;
  }

  const ageHours = (Date.now() - latestReport.modifiedAt) / (1000 * 60 * 60);
  if (ageHours > 168) {
    violations.push(`Runtime dependency failure-injection report is stale (> 168h): ${latestReport.entry}`);
  }

  if (!Array.isArray(report.violations)) {
    violations.push('Runtime dependency failure-injection report missing violations array');
  } else if (report.violations.length > 0) {
    violations.push(`Runtime dependency failure-injection report has ${report.violations.length} violation(s)`);
  }

  if (!Array.isArray(report.evidence)) {
    violations.push('Runtime dependency failure-injection report missing evidence array');
    return;
  }

  const evidenceText = report.evidence.join(' | ').toLowerCase();
  for (const scenario of requiredScenarioEvidence) {
    const satisfied = scenario.patterns.every((pattern) => evidenceText.includes(pattern));
    if (!satisfied) {
      violations.push(`Runtime dependency failure-injection report missing scenario evidence for ${scenario.key}`);
    }
  }

  if (!report.summary || typeof report.summary.totalViolations !== 'number') {
    violations.push('Runtime dependency failure-injection report missing summary.totalViolations');
  }
}

for (const relativePath of requiredFiles) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    violations.push(`Missing failure-injection asset: ${relativePath}`);
  }
}

if (!existsSync(releaseWorkflowPath)) {
  violations.push('Missing data cloud release workflow (.github/workflows/data-cloud-release.yml)');
} else {
  const releaseWorkflow = readFileSync(releaseWorkflowPath, 'utf8');
  for (const token of requiredReleaseTokens) {
    if (!releaseWorkflow.includes(token)) {
      violations.push(`Release workflow is missing strict failure-injection token: ${JSON.stringify(token)}`);
    }
  }

  if (releaseWorkflow.includes('smoke-e2e-strict') && releaseWorkflow.includes('continue-on-error: true')) {
    violations.push('Strict release jobs must not use continue-on-error: true');
  }
}

validateFailureInjectionReport();

const ciWorkflowPath = path.join(repoRoot, '.github/workflows/data-cloud-ci.yml');
if (!existsSync(ciWorkflowPath)) {
  violations.push('Missing data cloud advisory CI workflow (.github/workflows/data-cloud-ci.yml)');
} else {
  const ciWorkflow = readFileSync(ciWorkflowPath, 'utf8');
  for (const token of requiredCiTokens) {
    if (!ciWorkflow.includes(token)) {
      violations.push(`Advisory CI workflow is missing durability token: ${JSON.stringify(token)}`);
    }
  }
}

if (violations.length > 0) {
  console.error('Runtime failure-injection conformance failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Runtime failure-injection conformance passed.');
