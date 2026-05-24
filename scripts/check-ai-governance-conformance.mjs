#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const violations = [];

const requiredFiles = [
  '.github/workflows/agent-eval.yml',
  'scripts/check-agentic-lifecycle-action-contracts.mjs',
  'products/data-cloud/scripts/validate-agent-governance.sh',
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java',
];

for (const relativePath of requiredFiles) {
  if (!existsSync(path.join(repoRoot, relativePath))) {
    violations.push(`Missing AI governance artifact: ${relativePath}`);
  }
}

const routeRegistryPath = path.join(
  repoRoot,
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java',
);

if (existsSync(routeRegistryPath)) {
  const routeRegistry = readFileSync(routeRegistryPath, 'utf8');
  for (const token of [
    '/api/v1/ai/suggestions',
    '/api/v1/ai/suggestions/{id}/feedback',
    '/api/v1/governance/privacy/redact',
    '/api/v1/governance/policies/simulate',
    '/api/v1/action/learning/review/{reviewId}/approve',
    '/api/v1/action/learning/review/{reviewId}/reject',
  ]) {
    if (!routeRegistry.includes(token)) {
      violations.push(`RouteSecurityRegistry missing AI governance route: ${token}`);
    }
  }
}

const releaseWorkflowPath = path.join(repoRoot, '.github/workflows/data-cloud-release.yml');
if (existsSync(releaseWorkflowPath)) {
  const releaseWorkflow = readFileSync(releaseWorkflowPath, 'utf8');
  if (!releaseWorkflow.includes('pnpm check:ai-governance-conformance')) {
    violations.push('Data Cloud release workflow must execute check:ai-governance-conformance');
  }
}

const behavioralProofPath = path.join(
  repoRoot,
  '.kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json',
);

if (!existsSync(behavioralProofPath)) {
  violations.push('Missing AI governance behavioral proof evidence: .kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json');
} else {
  const proofSource = readFileSync(behavioralProofPath, 'utf8');
  let proof = null;
  try {
    proof = JSON.parse(proofSource);
  } catch (error) {
    violations.push(`AI governance behavioral proof evidence is not valid JSON: ${error.message}`);
  }

  if (proof) {
    if (proof.evidenceRun?.generatedBy !== 'scripts/check-ai-governance-behavioral-proof.mjs') {
      violations.push('AI governance behavioral proof must include script-generated evidenceRun.generatedBy metadata');
    }
    if (proof.evidenceRun?.source !== 'scripts/check-ai-governance-behavioral-proof.mjs') {
      violations.push('AI governance behavioral proof must include evidenceRun.source metadata');
    }
    if (proof.evidenceRun?.command !== 'pnpm check:ai-governance-behavioral-proof') {
      violations.push('AI governance behavioral proof must include evidenceRun.command metadata');
    }
    if (Number(proof.summary?.totalWarnings ?? 0) !== 0) {
      violations.push('AI governance behavioral proof must have zero warnings');
    }
    if (Number(proof.summary?.totalViolations ?? 0) !== 0) {
      violations.push('AI governance behavioral proof must have zero violations');
    }
    if (Array.isArray(proof.warnings) && proof.warnings.length > 0) {
      violations.push('AI governance behavioral proof warnings array must be empty');
    }
    if (Array.isArray(proof.violations) && proof.violations.length > 0) {
      violations.push('AI governance behavioral proof violations array must be empty');
    }
  }

  for (const forbiddenToken of [
    'falling back to posture checks',
    'fallback to posture checks',
    'Test execution failed',
    'Missing AI governance infrastructure',
    'Missing model registry',
    'Missing human approval proof',
  ]) {
    if (proofSource.includes(forbiddenToken)) {
      violations.push(`AI governance behavioral proof contains forbidden release evidence token: ${forbiddenToken}`);
    }
  }
}

const agentEvalWorkflowPath = path.join(repoRoot, '.github/workflows/agent-eval.yml');
if (existsSync(agentEvalWorkflowPath)) {
  const workflowSource = readFileSync(agentEvalWorkflowPath, 'utf8');
  for (const token of ['Agent Evaluation Flywheel', 'schedule:', 'Upload Eval Report']) {
    if (!workflowSource.includes(token)) {
      violations.push(`agent-eval workflow missing required token ${JSON.stringify(token)}`);
    }
  }
}

if (violations.length > 0) {
  console.error('AI governance conformance failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('AI governance conformance passed.');
