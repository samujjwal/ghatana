#!/usr/bin/env node

/**
 * Validation script for lifecycle run history capabilities.
 * Verifies that the kernel-lifecycle package provides durable run history storage and querying.
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

/**
 * Check if a file exists and contains required exports
 */
function checkModuleExports(modulePath, requiredExports) {
  if (!existsSync(modulePath)) {
    return { exists: false, missing: requiredExports.map(e => e.name) };
  }

  const source = readFileSync(modulePath, 'utf8');
  const missing = [];

  for (const item of requiredExports) {
    if (!source.includes(item.pattern)) {
      missing.push(item.name);
    }
  }

  return { exists: true, missing };
}

/**
 * Main validation
 */
function validate() {
  const errors = [];

  // Check RunStore module
  const runStorePath = path.join(
    repoRoot,
    'platform/typescript/kernel-lifecycle/src/domain/RunStore.ts'
  );
  const runStoreExports = [
    { name: 'RunStore', pattern: 'export interface RunStore' },
    { name: 'RunRecord', pattern: 'export interface RunRecord' },
    { name: 'RunQueryFilter', pattern: 'export interface RunQueryFilter' },
    { name: 'RunQueryResult', pattern: 'export interface RunQueryResult' },
    { name: 'InMemoryRunStore', pattern: 'export class InMemoryRunStore' },
  ];

  const runStoreCheck = checkModuleExports(runStorePath, runStoreExports);
  if (!runStoreCheck.exists) {
    errors.push('RunStore module does not exist');
  } else if (runStoreCheck.missing.length > 0) {
    errors.push(`RunStore missing exports: ${runStoreCheck.missing.join(', ')}`);
  }

  // Check RunStore has required methods
  if (runStoreCheck.exists) {
    const runStoreSource = readFileSync(runStorePath, 'utf8');
    const requiredMethods = [
      'createRun',
      'updateRun',
      'getRun',
      'queryRuns',
      'getLatestRun',
      'getRunningRuns',
      'deleteRun',
      'deleteRunsOlderThan',
    ];

    for (const method of requiredMethods) {
      if (!runStoreSource.includes(method)) {
        errors.push(`RunStore missing method: ${method}`);
      }
    }
  }

  // Check RunRecord has required fields
  if (runStoreCheck.exists) {
    const runStoreSource = readFileSync(runStorePath, 'utf8');
    const requiredFields = [
      'runId',
      'productId',
      'phase',
      'status',
      'startedAt',
      'completedAt',
      'durationMs',
      'metadata',
    ];

    for (const field of requiredFields) {
      if (!runStoreSource.includes(field)) {
        errors.push(`RunRecord missing field: ${field}`);
      }
    }
  }

  // Check RunQueryFilter has query capabilities
  if (runStoreCheck.exists) {
    const runStoreSource = readFileSync(runStorePath, 'utf8');
    const queryFields = [
      'productId',
      'phase',
      'status',
      'startedAfter',
      'startedBefore',
      'correlationId',
      'limit',
      'offset',
    ];

    for (const field of queryFields) {
      if (!runStoreSource.includes(field)) {
        errors.push(`RunQueryFilter missing field: ${field}`);
      }
    }
  }

  // Check CLI has run history commands (optional for now)
  const cliPath = path.join(repoRoot, 'scripts/kernel-product-new.mjs');
  if (existsSync(cliPath)) {
    const cliSource = readFileSync(cliPath, 'utf8');
    
    // Run history command is optional for Phase 1
    // This will be added in future phases
  } else {
    errors.push('kernel-product-new.mjs CLI does not exist');
  }

  // Check that executor integrates with RunStore
  const executorPath = path.join(
    repoRoot,
    'platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts'
  );
  if (existsSync(executorPath)) {
    const executorSource = readFileSync(executorPath, 'utf8');
    if (!executorSource.includes('RunStore') && !executorSource.includes('runStore')) {
      errors.push('ProductLifecycleExecutor does not integrate with RunStore');
    }
  } else {
    errors.push('ProductLifecycleExecutor module does not exist');
  }

  if (errors.length > 0) {
    console.error('Lifecycle run history validation failed:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('Lifecycle run history validation passed.');
}

validate();
