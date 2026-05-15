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

requireFile('platform/typescript/kernel-product-contracts/src/provider/LifecycleProviders.ts');
requireFile('platform/typescript/kernel-lifecycle/src/providers/LifecycleProviderContext.ts');
requireFile('platform/typescript/kernel-providers/src/factory/createBootstrapKernelProviders.ts');
requireFile('products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudKernelExtension.java');

requireAll('platform/typescript/kernel-product-contracts/src/provider/LifecycleProviders.ts', [
  'KERNEL_PROVIDER_MODES',
  '"bootstrap"',
  '"platform"',
  'LifecycleEventProvider',
  'LifecycleArtifactProvider',
  'LifecycleHealthProvider',
  'LifecycleProvenanceProvider',
  'LifecycleMemoryProvider',
  'LifecycleRuntimeTruthProvider',
  'KernelLifecycleProviderContext',
  'requireLifecycleProvider',
]);

requireAll('platform/typescript/kernel-lifecycle/src/providers/LifecycleProviderContext.ts', [
  'extends KernelLifecycleProviderContext',
  'requireLifecycleContextProvider',
  'Kernel ${context.mode} mode requires lifecycle provider',
]);

// New assertion: Platform mode must use Data Cloud-backed providers
requireAll('platform/typescript/kernel-lifecycle/src/providers/LifecycleProviderContext.ts', [
  'platform mode requires Data Cloud-backed',
  'events provider',
  'artifacts provider',
  'health provider',
  'approvals provider',
  'provenance provider',
  'memory provider',
  'runtimeTruth provider',
]);

requireAll('platform/typescript/kernel-providers/src/factory/createBootstrapKernelProviders.ts', [
  'mode: "bootstrap"',
  'FileLifecycleEventProvider',
  'FileArtifactProvider',
  'FileHealthProvider',
  'FileApprovalProvider',
  'FileProvenanceProvider',
  'FileRuntimeTruthProvider',
]);

requireAll('products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudKernelExtension.java', [
  'DataCloud',
  'Lifecycle',
  'Provider',
]);

if (errors.length > 0) {
  console.error('Kernel provider mode check failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('Kernel provider mode check passed.');
