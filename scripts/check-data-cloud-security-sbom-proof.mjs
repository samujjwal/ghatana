#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const SCRIPT_PATH = 'scripts/check-data-cloud-security-sbom-proof.mjs';
const WORKFLOW_PATH = '.github/workflows/data-cloud-release.yml';
const EVIDENCE_PATH = '.kernel/evidence/data-cloud-security-sbom-proof.json';
const COMMAND = 'pnpm check:data-cloud-security-sbom-proof';
const REQUIRED_TOKENS = [
  'security-scan-strict:',
  'needs: [build-and-test]',
  'Run dependency vulnerability check (blocking)',
  ':products:data-cloud:delivery:runtime-composition:dependencyCheckAnalyze',
  'Generate SBOM (blocking)',
  './gradlew cyclonedxBom --no-daemon',
  'Upload SBOM artifact',
  'name: data-cloud-sbom',
  '**/build/reports/bom.json',
  '**/build/reports/bom.xml',
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

export function createDataCloudSecuritySbomProof(root = process.cwd(), now = new Date()) {
  const head = currentGitSha(root);
  const workflowFullPath = path.join(root, WORKFLOW_PATH);
  const violations = [];
  const workflow = existsSync(workflowFullPath) ? readFileSync(workflowFullPath, 'utf8') : '';

  if (!workflow) {
    violations.push(`${WORKFLOW_PATH} is missing`);
  }

  for (const token of REQUIRED_TOKENS) {
    if (!workflow.includes(token)) {
      violations.push(`${WORKFLOW_PATH}: missing required security/SBOM token ${JSON.stringify(token)}`);
    }
  }

  if (!/security-scan-strict:[\s\S]*Generate SBOM \(blocking\)[\s\S]*Upload SBOM artifact/.test(workflow)) {
    violations.push(`${WORKFLOW_PATH}: security-scan-strict job must generate and upload SBOM before release bundle creation`);
  }
  if (!/release-gate:[\s\S]*security-scan-strict[\s\S]*Generate Data Cloud release evidence bundle/.test(workflow)) {
    violations.push(`${WORKFLOW_PATH}: release-gate job must depend on security-scan-strict before generating the release bundle`);
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
    source: {
      workflow: WORKFLOW_PATH,
    },
    summary: {
      requiredTokenCount: REQUIRED_TOKENS.length,
      violationCount: violations.length,
    },
    requiredTokens: REQUIRED_TOKENS,
    violations,
  };
}

export function writeDataCloudSecuritySbomProof(root = process.cwd(), evidence = createDataCloudSecuritySbomProof(root)) {
  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const evidence = createDataCloudSecuritySbomProof(root);
  writeDataCloudSecuritySbomProof(root, evidence);

  if (!evidence.pass) {
    console.error('Data Cloud security/SBOM proof failed:\n');
    for (const violation of evidence.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log(`Data Cloud security/SBOM proof written to ${EVIDENCE_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
