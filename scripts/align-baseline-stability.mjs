#!/usr/bin/env node

/**
 * Align baseline status with route contract stability.
 *
 * @doc.type script
 * @doc.purpose Validate baseline status alignment with route contract stability
 * @doc.layer scripts
 * @doc.pattern Validation
 */

import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

/**
 * Mapping from route stability to expected baseline status
 */
const STABILITY_TO_BASELINE = {
  stable: 'implemented',
  preview: 'partial',
  blocked: 'deferred',
  hidden: 'deferred',
  deferred: 'deferred',
  removed: 'removed',
};

/**
 * Parse JSON file
 */
function parseJson(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  return JSON.parse(content);
}

/**
 * Validate baseline status alignment with route stability
 */
function validateBaselineAlignment(routeContract, baseline) {
  const errors = [];
  const warnings = [];

  // Create a map from route path to stability
  const routeStabilityMap = new Map();
  for (const route of routeContract.routes) {
    routeStabilityMap.set(route.path, route.stability);
  }

  // Check each use case in baseline
  for (const usecase of baseline.usecases) {
    const routePath = usecase.iaRoute || usecase.webRoute;
    if (!routePath) {
      continue;
    }

    const stability = routeStabilityMap.get(routePath);
    if (!stability) {
      warnings.push(`Use case ${usecase.id} (${routePath}) has no matching route in contract`);
      continue;
    }

    const expectedStatus = STABILITY_TO_BASELINE[stability];
    if (usecase.status !== expectedStatus) {
      errors.push(
        `Use case ${usecase.id} (${routePath}): status "${usecase.status}" does not match route stability "${stability}" (expected "${expectedStatus}")`
      );
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

/**
 * Main validation function
 */
function main() {
  const routeContractPath = join(REPO_ROOT, 'products', 'phr', 'config', 'phr-route-contract.json');
  const baselinePath = join(REPO_ROOT, 'products', 'phr', 'config', 'phr-usecase-baseline.json');

  if (!existsSync(routeContractPath)) {
    console.log(`❌ Route contract not found: ${routeContractPath}`);
    process.exit(1);
  }

  if (!existsSync(baselinePath)) {
    console.log(`❌ Baseline not found: ${baselinePath}`);
    process.exit(1);
  }

  const routeContract = parseJson(routeContractPath);
  const baseline = parseJson(baselinePath);

  console.log('Validating baseline status alignment with route contract stability...\n');

  const result = validateBaselineAlignment(routeContract, baseline);

  if (result.errors.length > 0) {
    console.log('❌ Alignment errors:');
    for (const error of result.errors) {
      console.log(`   - ${error}`);
    }
  }

  if (result.warnings.length > 0) {
    console.log('⚠️  Warnings:');
    for (const warning of result.warnings) {
      console.log(`   - ${warning}`);
    }
  }

  if (result.errors.length === 0 && result.warnings.length === 0) {
    console.log('✅ All baseline statuses align with route contract stability');
  }

  console.log(`\nSummary: ${result.errors.length} errors, ${result.warnings.length} warnings`);

  if (result.errors.length > 0) {
    process.exit(1);
  }
}

main();
