#!/usr/bin/env node

/**
 * KER-T02: Kernel validator for route contract to page/API/policy/test parity.
 * 
 * This script validates the PHR route contract against the kernel's
 * ProductRouteContractSchema to ensure parity across all layers:
 * - Frontend pages
 * - Backend APIs
 * - Policy enforcement
 * - Test coverage
 */

import { readFileSync } from 'fs';
import { fileURLToPath, pathToFileURL } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const routeContractPath = join(__dirname, '..', 'products', 'phr', 'config', 'phr-route-contract.json');
const kernelContractsPath = join(__dirname, '..', 'platform', 'typescript', 'kernel-product-contracts', 'dist', 'route', 'ProductRouteContract.js');
const kernelContractsUrl = pathToFileURL(kernelContractsPath).href;

let parseProductRouteContract;

// Try to load kernel validator
try {
  const kernelModule = await import(kernelContractsUrl);
  parseProductRouteContract = kernelModule.parseProductRouteContract;
} catch (error) {
  console.error('Failed to load kernel validator:', error.message);
  console.error('Ensure kernel-product-contracts is built: cd platform/typescript/kernel-product-contracts && pnpm build');
  process.exit(1);
}

const routeContract = JSON.parse(readFileSync(routeContractPath, 'utf-8'));

console.log('Validating PHR route contract against kernel schema...\n');

try {
  parseProductRouteContract(routeContract);
  console.log('✅ Route contract validation passed');
  console.log(`\n${routeContract.routes.length} routes validated against kernel schema`);
  process.exit(0);
} catch (error) {
  console.error('❌ Route contract validation failed:');
  console.error(error.message);
  
  // Format Zod errors nicely
  if (error.errors) {
    console.error('\nValidation errors:');
    for (const err of error.errors) {
      const path = err.path.join('.');
      console.error(`  - ${path}: ${err.message}`);
    }
  }
  
  process.exit(1);
}
