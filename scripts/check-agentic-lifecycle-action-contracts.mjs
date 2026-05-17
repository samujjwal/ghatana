#!/usr/bin/env node
// Authoritative Source: docs/AI_GOVERNANCE_CONTRACTS.md

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const errors = [];

function read(relativePath) {
  return readFileSync(join(repoRoot, relativePath), 'utf8');
}

function requireFile(relativePath) {
  if (!existsSync(join(repoRoot, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
  }
}

function requireIncludes(relativePath, needle, label = relativePath) {
  const source = read(relativePath);
  if (!source.includes(needle)) {
    errors.push(`${label} must include ${needle}`);
  }
}

function requireAll(relativePath, needles, label = relativePath) {
  for (const needle of needles) {
    requireIncludes(relativePath, needle, label);
  }
}

requireFile('platform/typescript/kernel-product-contracts/src/agentic/AgentLifecycleActionRequest.ts');
requireFile('platform/typescript/kernel-product-contracts/src/agentic/AgentLifecycleActionResult.ts');
requireFile('platform/typescript/kernel-lifecycle/src/agentic/AgentLifecycleActionService.ts');
requireFile('platform/typescript/kernel-lifecycle/src/agentic/__tests__/AgentLifecycleActionService.test.ts');
requireFile('products/data-cloud/planes/action/gateway/src/__tests__/agentic-lifecycle-actions.test.ts');

requireAll('platform/typescript/kernel-product-contracts/src/agentic/AgentLifecycleActionRequest.ts', [
  'AgentLifecycleActionRequestSchema',
  'RAW_COMMAND_VALUE_PATTERN',
  'gradle',
  'pnpm',
  'docker',
  'kubectl',
  'strict()',
  'requiredApprovals',
  'requiredVerification',
  'rollbackPlanRef',
]);

requireAll('platform/typescript/kernel-product-contracts/src/agentic/AgentLifecycleActionResult.ts', [
  'AgentLifecycleActionResultSchema',
  'policyDecision',
  'masteryDecision',
  'approvalDecision',
  'lifecycleRunRef',
  'rollbackReadiness',
]);

requireAll('platform/typescript/kernel-lifecycle/src/agentic/AgentLifecycleActionService.ts', [
  'parseAgentLifecycleActionRequest',
  'evaluatePolicy',
  'evaluateMastery',
  'evaluateApproval',
  'evaluateVerification',
  'planner.plan',
  'executor.executePlan',
  'recordProvenance',
  'recordRuntimeTruth',
  'recordMemory',
  'AgentLifecycleActionResultSchema.parse',
]);

requireAll('products/data-cloud/planes/action/gateway/src/__tests__/agentic-lifecycle-actions.test.ts', [
  'schemaVersion: \'1.0.0\'',
  'requestedAction: \'create-lifecycle-plan\'',
  'lifecyclePhase: \'build\'',
  'without proxying to raw backend tools',
  'fails closed when the governed Kernel service is not configured',
  'surfaces service validation failures for raw command attempts',
]);

requireAll('platform/typescript/kernel-lifecycle/src/agentic/__tests__/AgentLifecycleActionService.test.ts', [
  'executes after approval and writes provenance through provider context',
  'recordProvenance',
  'recordRuntimeTruth',
  'recordMemory',
  'fails contract validation for raw command requests',
  'fails contract validation when evidence refs are missing for required verification',
]);

const gatewayTestSource = read('products/data-cloud/planes/action/gateway/src/__tests__/agentic-lifecycle-actions.test.ts');
for (const legacyNeedle of [
  'schemaVersion: \'agent.lifecycle.action.request.v1\'',
  'requestedAction: \'plan\'',
  'lifecyclePhase: \'plan\'',
]) {
  if (gatewayTestSource.includes(legacyNeedle)) {
    errors.push(`gateway agentic lifecycle test must not use legacy shape: ${legacyNeedle}`);
  }
}

if (errors.length > 0) {
  console.error('Agentic lifecycle action contract check failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('Agentic lifecycle action contract check passed.');
