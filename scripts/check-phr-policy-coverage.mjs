#!/usr/bin/env node

/**
 * T-008: Check PHR policy coverage.
 * Validates that every PHI route has a policy ID and test.
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const PHR_CONFIG_DIR = resolve(process.cwd(), 'products/phr/config');
const PHR_BACKEND_DIR = resolve(process.cwd(), 'products/phr/src/main/java/com/ghatana/phr');

// Load JSON contract
const jsonContractPath = resolve(PHR_CONFIG_DIR, 'phr-route-contract.json');
if (!existsSync(jsonContractPath)) {
  console.error('ERROR: phr-route-contract.json not found');
  process.exit(1);
}
const jsonContract = JSON.parse(readFileSync(jsonContractPath, 'utf-8'));

// PHI-related route groups
const PHI_ROUTE_GROUPS = ['care', 'governance', 'clinical'];

let hasErrors = false;

// Check each route for policy ID
for (const route of jsonContract.routes) {
  if (PHI_ROUTE_GROUPS.includes(route.group)) {
    if (!route.policyId) {
      console.error(`ERROR: PHI route '${route.path}' is missing policyId`);
      hasErrors = true;
    }
    
    if (!route.testId) {
      console.warn(`WARNING: PHI route '${route.path}' is missing testId`);
    }
  }
}

// Check backend route files for policy evaluation
const backendRouteFiles = [
  'api/routes/PhrPatientRecordRoutes.java',
  'api/routes/PhrConsentRoutes.java',
  'api/routes/PhrDocumentImagingRoutes.java',
  'api/routes/PhrEmergencyRoutes.java',
  'api/routes/PhrAppointmentRoutes.java',
  'api/routes/PhrMedicationRoutes.java',
  'api/routes/PhrImmunizationRoutes.java',
  'api/routes/PhrConditionRoutes.java',
  'api/routes/PhrObservationRoutes.java',
];

for (const routeFile of backendRouteFiles) {
  const routePath = resolve(PHR_BACKEND_DIR, routeFile);
  if (existsSync(routePath)) {
    const routeContent = readFileSync(routePath, 'utf-8');
    
    // Check if route file uses policy evaluator
    if (!routeContent.includes('PhrPolicyEvaluator') && !routeContent.includes('policyEvaluator')) {
      console.warn(`WARNING: Backend route file '${routeFile}' may not use policy evaluator`);
    }
    
    // Check if route file has tests
    const testPath = routePath.replace('.java', 'Test.java');
    if (!existsSync(testPath)) {
      console.warn(`WARNING: Backend route file '${routeFile}' may not have corresponding test file`);
    }
  }
}

if (hasErrors) {
  console.error('FAIL: Policy coverage check failed');
  process.exit(1);
}

console.log('PASS: Policy coverage check passed');
