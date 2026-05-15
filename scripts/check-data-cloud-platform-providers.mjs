#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const errors = [];

function read(relativePath) {
  const absolutePath = join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    errors.push(`Missing required file: ${relativePath}`);
    return '';
  }
  return readFileSync(absolutePath, 'utf8');
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

function isDirectory(pathValue) {
  try {
    return statSync(pathValue).isDirectory();
  } catch {
    return false;
  }
}

function isFile(pathValue) {
  try {
    return statSync(pathValue).isFile();
  } catch {
    return false;
  }
}

function collectFiles(rootDir, extensions) {
  if (!isDirectory(rootDir)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(rootDir)) {
    const fullPath = join(rootDir, entry);
    if (isDirectory(fullPath)) {
      if (entry === 'node_modules' || entry === 'dist' || entry === '__tests__') {
        continue;
      }
      files.push(...collectFiles(fullPath, extensions));
      continue;
    }
    if (!isFile(fullPath)) {
      continue;
    }
    if (extensions.some((extension) => fullPath.endsWith(extension))) {
      files.push(fullPath);
    }
  }
  return files;
}

const providerContractPath =
  'platform/typescript/kernel-product-contracts/src/provider/LifecycleProviders.ts';
const providerContextPath = 'platform/typescript/kernel-lifecycle/src/providers/LifecycleProviderContext.ts';
const dataCloudExtensionPath =
  'products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudKernelExtension.java';

requireFile(providerContractPath);
requireFile(providerContextPath);
requireFile(dataCloudExtensionPath);

requireIncludes(providerContractPath, 'LifecycleEventProvider');
requireIncludes(providerContractPath, 'LifecycleArtifactProvider');
requireIncludes(providerContractPath, 'LifecycleHealthProvider');
requireIncludes(providerContractPath, 'LifecycleProvenanceProvider');
requireIncludes(providerContractPath, 'LifecycleMemoryProvider');
requireIncludes(providerContractPath, 'LifecycleRuntimeTruthProvider');
requireIncludes(providerContractPath, 'KernelProviderModeRequirements');

requireIncludes(providerContextPath, 'Kernel platform mode requires Data Cloud-backed');
requireIncludes(providerContextPath, 'events provider');
requireIncludes(providerContextPath, 'artifacts provider');
requireIncludes(providerContextPath, 'health provider');
requireIncludes(providerContextPath, 'approvals provider');
requireIncludes(providerContextPath, 'provenance provider');
requireIncludes(providerContextPath, 'memory provider');
requireIncludes(providerContextPath, 'runtimeTruth provider');

requireIncludes(dataCloudExtensionPath, 'registerPlatformProviders');
requireIncludes(dataCloudExtensionPath, 'DataCloudEventProvider');
requireIncludes(dataCloudExtensionPath, 'DataCloudArtifactProvider');
requireIncludes(dataCloudExtensionPath, 'DataCloudHealthProvider');
requireIncludes(dataCloudExtensionPath, 'DataCloudProvenanceProvider');
requireIncludes(dataCloudExtensionPath, 'DataCloudMemoryProvider');
requireIncludes(dataCloudExtensionPath, 'DataCloudRuntimeTruthProvider');
requireIncludes(dataCloudExtensionPath, 'DataCloudPolicyEvidenceProvider');

const platformTypeScriptRoot = join(repoRoot, 'platform', 'typescript');
const platformFiles = collectFiles(platformTypeScriptRoot, ['.ts', '.tsx', '.js', '.mjs']);
for (const filePath of platformFiles) {
  const source = readFileSync(filePath, 'utf8');
  if (source.includes('products/data-cloud')) {
    const relativePath = filePath.slice(repoRoot.length + 1).replace(/\\/g, '/');
    errors.push(`Platform package must not import product internals directly: ${relativePath}`);
  }
}

if (errors.length > 0) {
  console.error('Data Cloud platform provider check failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

console.log('Data Cloud platform provider check passed.');
