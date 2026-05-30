#!/usr/bin/env node

/**
 * Validates PHR route contract for completeness
 * RTE-001: Verify every route has required fields
 */

import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const routeContractPath = join(__dirname, '..', 'products', 'phr', 'config', 'phr-route-contract.json');
const routeContract = JSON.parse(readFileSync(routeContractPath, 'utf-8'));

const REQUIRED_FIELDS = [
  'path',
  'label',
  'description',
  'group',
  'minimumRole',
  'personas',
  'tiers',
  'actions',
  'cards',
  'stability',
  'surface',
  'i18nKey',
  'descriptionI18nKey',
  'routeType',
];

const CONDITIONAL_FIELDS = {
  apiEndpoint: ['stable'],
  policyId: ['stable'],
  testId: ['stable'],
};

let errors = 0;
let warnings = 0;

console.log('Validating PHR route contract...\n');

for (const route of routeContract.routes) {
  const routeId = route.path || 'UNKNOWN';
  
  // Check required fields
  for (const field of REQUIRED_FIELDS) {
    if (!(field in route) || route[field] === undefined || route[field] === null) {
      console.error(`❌ Route ${routeId}: Missing required field '${field}'`);
      errors++;
    }
  }
  
  // Check conditional fields based on stability
  const stability = route.stability;
  for (const [field, allowedStabilities] of Object.entries(CONDITIONAL_FIELDS)) {
    if (allowedStabilities.includes(stability)) {
      if (!(field in route) || route[field] === undefined || route[field] === null) {
        console.error(`❌ Route ${routeId}: Missing field '${field}' for stability '${stability}'`);
        errors++;
      }
    }
  }
  
  // Validate stability value
  if (!['stable', 'hidden', 'blocked'].includes(stability)) {
    console.error(`❌ Route ${routeId}: Invalid stability value '${stability}'`);
    errors++;
  }
  
  // Validate hidden routes have visibilityReason
  if (stability === 'hidden' && !route.visibilityReason) {
    console.warn(`⚠️  Route ${routeId}: Hidden route missing visibilityReason`);
    warnings++;
  }
  
  // Validate surface array
  if (route.surface && !Array.isArray(route.surface)) {
    console.error(`❌ Route ${routeId}: surface must be an array`);
    errors++;
  }
  
  // Validate personas is array
  if (!Array.isArray(route.personas)) {
    console.error(`❌ Route ${routeId}: personas must be an array`);
    errors++;
  }
  
  // Validate tiers is array
  if (!Array.isArray(route.tiers)) {
    console.error(`❌ Route ${routeId}: tiers must be an array`);
    errors++;
  }
}

console.log(`\n${routeContract.routes.length} routes validated`);
console.log(`Errors: ${errors}`);
console.log(`Warnings: ${warnings}`);

if (errors > 0) {
  process.exit(1);
}

console.log('✅ Route contract validation passed');
