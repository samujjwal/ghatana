#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const releaseWorkflowPath = path.join(repoRoot, '.github/workflows/data-cloud-release.yml');

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

const violations = [];

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
