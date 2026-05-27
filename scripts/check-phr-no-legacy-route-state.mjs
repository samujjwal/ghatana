#!/usr/bin/env node

/**
 * T-002: Check PHR no legacy route state.
 * Fails if deprecated/removed/migration route metadata is found.
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const PHR_CONFIG_DIR = resolve(process.cwd(), 'products/phr/config');

// Load JSON contract
const jsonContractPath = resolve(PHR_CONFIG_DIR, 'phr-route-contract.json');
if (!existsSync(jsonContractPath)) {
  console.error('ERROR: phr-route-contract.json not found');
  process.exit(1);
}
const jsonContract = JSON.parse(readFileSync(jsonContractPath, 'utf-8'));

// Check for legacy stability states
const LEGACY_STABILITIES = ['deprecated', 'removed', 'migration'];
const LEGACY_LIFECYCLE_STATUSES = ['deprecated', 'removed', 'migration', 'legacy'];

let hasErrors = false;

// Check each route for legacy stability
for (const route of jsonContract.routes) {
  if (route.stability && LEGACY_STABILITIES.includes(route.stability.toLowerCase())) {
    console.error(`ERROR: Route '${route.path}' has legacy stability: ${route.stability}`);
    hasErrors = true;
  }
  
  // Check lifecycle status if present
  if (route.lifecycle && route.lifecycle.status) {
    if (LEGACY_LIFECYCLE_STATUSES.includes(route.lifecycle.status.toLowerCase())) {
      console.error(`ERROR: Route '${route.path}' has legacy lifecycle status: ${route.lifecycle.status}`);
      hasErrors = true;
    }
  }
}

// Check for featureFlag = false (disabled routes)
for (const route of jsonContract.routes) {
  if (route.featureFlag === false) {
    console.warn(`WARNING: Route '${route.path}' has featureFlag = false (disabled)`);
  }
}

// Check for empty or missing stability
for (const route of jsonContract.routes) {
  if (!route.stability) {
    console.warn(`WARNING: Route '${route.path}' is missing stability field`);
  }
}

if (hasErrors) {
  console.error('FAIL: Legacy route state check failed');
  process.exit(1);
}

console.log('PASS: No legacy route states found');
