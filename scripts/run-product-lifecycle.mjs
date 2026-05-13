#!/usr/bin/env node

/**
 * Run product lifecycle phase
 *
 * Usage:
 *   node scripts/run-product-lifecycle.mjs <productId> <phase> [options]
 *
 * Examples:
 *   node scripts/run-product-lifecycle.mjs digital-marketing build
 *   node scripts/run-product-lifecycle.mjs digital-marketing dev --dry-run
 */

import { ProductLifecyclePlanner } from '../platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.js';

async function main() {
  const args = process.argv.slice(2);

  if (args.length < 2) {
    console.error('Usage: node scripts/run-product-lifecycle.mjs <productId> <phase> [options]');
    console.error('');
    console.error('Options:');
    console.error('  --dry-run   - Plan only, do not execute');
    console.error('  --surfaces  - Comma-separated list of surfaces to include');
    console.error('');
    console.error('Examples:');
    console.error('  node scripts/run-product-lifecycle.mjs digital-marketing build');
    console.error('  node scripts/run-product-lifecycle.mjs digital-marketing dev --dry-run');
    console.error('  node scripts/run-product-lifecycle.mjs digital-marketing build --surfaces=backend-api');
    process.exit(1);
  }

  const [productId, phase, ...options] = args;

  const dryRun = options.includes('--dry-run');
  const surfaceSelector = options
    .filter((opt) => opt.startsWith('--surfaces='))
    .map((opt) => opt.split('=')[1].split(','));

  try {
    const planner = new ProductLifecyclePlanner();
    const plan = await planner.plan(productId, phase, {
      surfaceSelector: surfaceSelector.length > 0 ? surfaceSelector : undefined,
    });

    console.log(`Executing ${phase} phase for ${productId}`);
    console.log(`Surfaces: ${plan.surfaces.join(', ')}`);
    console.log(`Estimated Duration: ${plan.estimatedDurationMs}ms`);
    console.log('');
    console.log('Steps to execute:');
    plan.steps.forEach((step, index) => {
      console.log(`  ${index + 1}. ${step.description}`);
    });

    if (dryRun) {
      console.log('');
      console.log('Dry-run mode - not executing');
      process.exit(0);
    }

    // TODO: Implement actual execution via toolchain adapters
    console.log('');
    console.log('Execution not yet implemented');
    process.exit(1);
  } catch (error) {
    console.error(`Error: ${error.message}`);
    process.exit(1);
  }
}

main();
