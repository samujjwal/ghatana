#!/usr/bin/env node

/**
 * Validation script for lifecycle explain and recover capabilities.
 * Verifies that the kernel-lifecycle package provides explain and recover functionality.
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

  // Check LifecycleRecoveryGuidance module
  const recoveryPath = path.join(
    repoRoot,
    'platform/typescript/kernel-lifecycle/src/recovery/LifecycleRecoveryGuidance.ts'
  );
  const recoveryExports = [
    { name: 'getRecoveryGuidance', pattern: 'export function getRecoveryGuidance' },
    { name: 'inferFailureCategory', pattern: 'export function inferFailureCategory' },
    { name: 'formatRecoveryGuidance', pattern: 'export function formatRecoveryGuidance' },
    { name: 'LifecycleRecoveryGuidance', pattern: 'export interface LifecycleRecoveryGuidance' },
    { name: 'RecoveryAction', pattern: 'export interface RecoveryAction' },
  ];

  const recoveryCheck = checkModuleExports(recoveryPath, recoveryExports);
  if (!recoveryCheck.exists) {
    errors.push('LifecycleRecoveryGuidance module does not exist');
  } else if (recoveryCheck.missing.length > 0) {
    errors.push(`LifecycleRecoveryGuidance missing exports: ${recoveryCheck.missing.join(', ')}`);
  }

  // Check ProductLifecyclePlanner has explain capability
  const plannerPath = path.join(
    repoRoot,
    'platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts'
  );
  const plannerExports = [
    { name: 'generatePlanExplain', pattern: 'generatePlanExplain' },
  ];

  const plannerCheck = checkModuleExports(plannerPath, plannerExports);
  if (!plannerCheck.exists) {
    errors.push('ProductLifecyclePlanner module does not exist');
  } else if (plannerCheck.missing.length > 0) {
    errors.push(`ProductLifecyclePlanner missing explain methods: ${plannerCheck.missing.join(', ')}`);
  }

  // Check CLI has explain and recover commands (optional for now)
  const cliPath = path.join(repoRoot, 'scripts/kernel-product-new.mjs');
  if (existsSync(cliPath)) {
    const cliSource = readFileSync(cliPath, 'utf8');
    
    // Explain/recover commands are optional for Phase 1
    // These will be added in future phases
  } else {
    errors.push('kernel-product-new.mjs CLI does not exist');
  }

  // Check that recovery guidance has comprehensive failure categories
  if (recoveryCheck.exists) {
    const recoverySource = readFileSync(recoveryPath, 'utf8');
    const requiredCategories = [
      'adapter-toolchain-missing',
      'adapter-dependency-error',
      'policy-authentication-failed',
      'policy-tenant-isolation-violation',
      'policy-consent-verification-failed',
      'interaction-handler-not-found',
      'environment-blocked',
      'test-failure',
      'gate-failure',
    ];

    for (const category of requiredCategories) {
      if (!recoverySource.includes(category)) {
        errors.push(`Recovery guidance missing failure category: ${category}`);
      }
    }
  }

  if (errors.length > 0) {
    console.error('Lifecycle explain/recover validation failed:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('Lifecycle explain/recover validation passed.');
}

validate();
