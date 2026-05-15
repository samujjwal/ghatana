#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const errors = [];

function read(relativePath) {
  if (!existsSync(join(repoRoot, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
    return '';
  }
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

requireFile('platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts');
requireFile('platform/typescript/kernel-lifecycle/src/events/KernelLifecycleEventEmitter.ts');
requireFile('platform/typescript/kernel-lifecycle/src/manifest/LifecycleManifestWriter.ts');
requireFile('platform/typescript/kernel-toolchains/src/ToolchainAdapter.ts');
requireFile('platform/typescript/kernel-lifecycle/src/__tests__/ProductLifecycleExecutor.test.ts');
requireFile('platform/typescript/kernel-lifecycle/src/events/__tests__/KernelLifecycleEventEmitter.test.ts');
requireFile('platform/typescript/kernel-lifecycle/src/manifest/__tests__/LifecycleManifestWriter.test.ts');

requireAll('platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts', [
  'emitLifecyclePhaseStart',
  'emitLifecyclePhaseComplete',
  'emitLifecycleStepStart',
  'emitLifecycleStepComplete',
  'emitGateEvaluated',
  'aggregateLifecycleHealth',
  'writeRequiredManifests',
  'failClosedWhenRequiredGateFailed',
  'failClosedWhenApprovalRequired',
  'failClosedWhenRequiredArtifactsMissing',
]);

requireAll('platform/typescript/kernel-lifecycle/src/events/KernelLifecycleEventEmitter.ts', [
  'lifecycle.phase.started',
  'lifecycle.phase.completed',
  'lifecycle.step.started',
  'lifecycle.step.completed',
  'lifecycle.gate.evaluated',
  'lifecycle.artifact.recorded',
  'lifecycle.deployment.completed',
  'lifecycle.health.checked',
  'correlationId',
  'appendEvent',
  'telemetryProvider.emitEvent',
]);

requireAll('platform/typescript/kernel-lifecycle/src/manifest/LifecycleManifestWriter.ts', [
  'lifecycle-result',
  'gate-result-manifest',
  'artifact-manifest',
  'deployment-manifest',
  'verify-health-report',
  'lifecycle-health-snapshot',
  'lifecycle-events',
  'writeRequiredManifests',
]);

requireAll('platform/typescript/kernel-toolchains/src/ToolchainAdapter.ts', [
  'ToolchainExecutionObservability',
  'commandId',
  'durationMs',
  'exitCode',
  'stdoutBytes',
  'stderrBytes',
  'stdoutTruncated',
  'stderrTruncated',
  'outputLimitBytes',
]);

requireAll('platform/typescript/kernel-lifecycle/src/__tests__/ProductLifecycleExecutor.test.ts', [
  'required gate',
  'fails closed when required approvals lack a provider',
  'artifact-missing',
]);

if (errors.length > 0) {
  console.error('Kernel lifecycle truth check failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('Kernel lifecycle truth check passed.');
