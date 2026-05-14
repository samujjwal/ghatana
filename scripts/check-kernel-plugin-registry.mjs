#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const registryPath = join(repoRoot, 'config', 'kernel-plugin-registry.json');
const schemaPath = join(repoRoot, 'config', 'kernel-plugin-registry-schema.json');

const validKinds = new Set([
  'pre-phase',
  'post-phase',
  'pre-gate',
  'post-gate',
  'pre-deployment',
  'post-deployment',
  'platform-plugin',
  'product-plugin',
]);

const validStatuses = new Set(['declared', 'implemented']);
const validMaturities = new Set(['declared-only', 'pilot', 'implemented']);
const validHooks = new Set([
  'onProductRegistered',
  'onProductBootstrapped',
  'onProductDevStarted',
  'onProductValidated',
  'onProductTested',
  'onProductBuildStarted',
  'onProductBuildCompleted',
  'onProductPackaged',
  'onProductDeployStarted',
  'onProductDeployed',
  'onProductVerified',
  'onProductPromoted',
  'onProductRolledBack',
  'onProductRetired',
]);

function loadJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function isNamespacedCapability(capability) {
  return typeof capability === 'string' && /^[a-z0-9-]+\.[a-z0-9][a-z0-9.-]*$/.test(capability);
}

function assertArray(entry, field, id, errors) {
  if (!Array.isArray(entry[field])) {
    errors.push(`Plugin "${id}" must have array field "${field}"`);
    return [];
  }
  return entry[field];
}

function checkPlugin(id, entry, errors, seenIds) {
  if (seenIds.has(entry.id)) {
    errors.push(`Duplicate plugin id "${entry.id}"`);
  }
  seenIds.add(entry.id);

  if (entry.id !== id) {
    errors.push(`Plugin entry key "${id}" does not match entry.id "${entry.id}"`);
  }
  if (typeof entry.name !== 'string' || entry.name.length === 0) {
    errors.push(`Plugin "${id}" is missing name`);
  }
  if (!validKinds.has(entry.kind)) {
    errors.push(`Plugin "${id}" has invalid kind "${entry.kind}"`);
  }
  if (!validStatuses.has(entry.status)) {
    errors.push(`Plugin "${id}" has invalid status "${entry.status}"`);
  }
  if (typeof entry.safeForDefault !== 'boolean') {
    errors.push(`Plugin "${id}" must declare boolean safeForDefault`);
  }
  if (!validMaturities.has(entry.maturity)) {
    errors.push(`Plugin "${id}" has invalid maturity "${entry.maturity}"`);
  }
  if (typeof entry.ownerLayer !== 'string' || entry.ownerLayer.length === 0) {
    errors.push(`Plugin "${id}" must declare ownerLayer`);
  }
  if (!Array.isArray(entry.currentLimitations) || entry.currentLimitations.length === 0) {
    errors.push(`Plugin "${id}" must declare currentLimitations`);
  }

  const capabilities = assertArray(entry, 'capabilities', id, errors);
  if (capabilities.length === 0) {
    errors.push(`Plugin "${id}" must declare at least one capability`);
  }
  for (const capability of capabilities) {
    if (!isNamespacedCapability(capability)) {
      errors.push(`Plugin "${id}" capability "${String(capability)}" must be namespaced`);
    }
  }

  for (const hook of assertArray(entry, 'lifecycleHooks', id, errors)) {
    if (!validHooks.has(hook)) {
      errors.push(`Plugin "${id}" has unknown lifecycle hook "${String(hook)}"`);
    }
  }
  assertArray(entry, 'healthOutput', id, errors);
  assertArray(entry, 'gateOutput', id, errors);
  const tests = assertArray(entry, 'tests', id, errors);

  if (entry.safeForDefault === true && entry.status !== 'implemented') {
    errors.push(`Plugin "${id}" has safeForDefault=true but is not implemented`);
  }
  if (entry.maturity === 'declared-only' && entry.safeForDefault === true) {
    errors.push(`Plugin "${id}" is declared-only and cannot be safeForDefault`);
  }
  if (entry.status === 'implemented') {
    if (typeof entry.implementationRef !== 'string' || entry.implementationRef.length === 0) {
      errors.push(`Plugin "${id}" is implemented but missing implementationRef`);
    } else {
      const implementationPath = join(repoRoot, entry.implementationRef);
      if (!existsSync(implementationPath)) {
        errors.push(`Plugin "${id}" implementation file does not exist: ${entry.implementationRef}`);
      }
      if (entry.kind === 'platform-plugin' && entry.implementationRef.includes('/products/')) {
        errors.push(`Platform plugin "${id}" implementationRef must not point into products/*`);
      }
    }
    if (tests.length === 0) {
      errors.push(`Plugin "${id}" is implemented but has no tests`);
    }
    for (const testPath of tests) {
      if (!existsSync(join(repoRoot, testPath))) {
        errors.push(`Plugin "${id}" test file does not exist: ${testPath}`);
      }
    }
  }
}

function main() {
  if (!existsSync(registryPath)) {
    throw new Error(`Plugin registry not found at ${registryPath}`);
  }
  if (!existsSync(schemaPath)) {
    throw new Error(`Plugin registry schema not found at ${schemaPath}`);
  }

  loadJson(schemaPath);
  const registry = loadJson(registryPath);
  const errors = [];

  if (typeof registry.version !== 'string') {
    errors.push('Registry is missing version');
  }
  if (!registry.plugins || typeof registry.plugins !== 'object') {
    errors.push('Registry is missing plugins');
  } else {
    const seenIds = new Set();
    for (const [id, entry] of Object.entries(registry.plugins)) {
      checkPlugin(id, entry, errors, seenIds);
    }
  }

  if (errors.length > 0) {
    console.error('Plugin registry validation failed:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log(`Plugin registry validation passed for ${Object.keys(registry.plugins).length} plugins.`);
}

main();
