#!/usr/bin/env node

/**
 * Print product lifecycle plan
 *
 * Usage:
 *   node scripts/print-product-lifecycle-plan.mjs <productId> <phase>
 *
 * Examples:
 *   node scripts/print-product-lifecycle-plan.mjs digital-marketing build
 */

import { ProductLifecyclePlanner } from '../platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.js';

async function main() {
  const args = process.argv.slice(2);

  if (args.length < 2) {
    console.error('Usage: node scripts/print-product-lifecycle-plan.mjs <productId> <phase>');
    process.exit(1);
  }

  const [productId, phase] = args;

  try {
    const planner = new ProductLifecyclePlanner();
    const plan = await planner.plan(productId, phase);

    console.log(`Lifecycle Plan for ${productId} - ${phase}`);
    console.log(`========================================`);
    console.log(`Surfaces: ${plan.surfaces.join(', ')}`);
    console.log(`Estimated Duration: ${plan.estimatedDurationMs}ms`);
    console.log('');
    console.log('Steps:');
    plan.steps.forEach((step, index) => {
      console.log(`  ${index + 1}. ${step.description}`);
      console.log(`     Adapter: ${step.adapter}`);
      console.log(`     Surface: ${step.surface}`);
      if (step.dependsOn.length > 0) {
        console.log(`     Depends on: ${step.dependsOn.join(', ')}`);
      }
    });
  } catch (error) {
    console.error(`Error: ${error.message}`);
    process.exit(1);
  }
}

main();
