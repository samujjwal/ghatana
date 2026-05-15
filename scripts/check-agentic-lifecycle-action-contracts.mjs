#!/usr/bin/env node

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
  'AgentLifecycleActionRequestSchema.parse',
  'evaluatePolicy',
  'evaluateMastery',
  'evaluateApproval',
  'evaluateVerification',
  'planner.plan',
  'executor.executePlan',
  'recordProvenance',
  'AgentLifecycleActionResultSchema.parse',
]);

requireAll('products/data-cloud/planes/action/gateway/src/__tests__/agentic-lifecycle-actions.test.ts', [
  'without proxying to raw backend tools',
  'fails closed when the governed Kernel service is not configured',
  'surfaces service validation failures for raw command attempts',
]);

if (errors.length > 0) {
  console.error('Agentic lifecycle action contract check failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('Agentic lifecycle action contract check passed.');
