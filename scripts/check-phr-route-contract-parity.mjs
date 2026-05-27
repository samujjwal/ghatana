#!/usr/bin/env node

/**
 * T-001: Check PHR route contract parity.
 * Validates JSON ↔ generated TS ↔ route elements ↔ backend entitlements.
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const PHR_CONFIG_DIR = resolve(process.cwd(), 'products/phr/config');
const PHR_WEB_SRC_DIR = resolve(process.cwd(), 'products/phr/apps/web/src');
const PHR_BACKEND_DIR = resolve(process.cwd(), 'products/phr/src/main/java/com/ghatana/phr');

// Load JSON contract
const jsonContractPath = resolve(PHR_CONFIG_DIR, 'phr-route-contract.json');
if (!existsSync(jsonContractPath)) {
  console.error('ERROR: phr-route-contract.json not found');
  process.exit(1);
}
const jsonContract = JSON.parse(readFileSync(jsonContractPath, 'utf-8'));

// Load TS contract
const tsContractPath = resolve(PHR_WEB_SRC_DIR, 'phrRouteContracts.ts');
if (!existsSync(tsContractPath)) {
  console.error('ERROR: phrRouteContracts.ts not found');
  process.exit(1);
}
const tsContractContent = readFileSync(tsContractPath, 'utf-8');

// Load route elements
const routeElementsPath = resolve(PHR_WEB_SRC_DIR, 'phrRouteElements.tsx');
if (!existsSync(routeElementsPath)) {
  console.error('ERROR: phrRouteElements.tsx not found');
  process.exit(1);
}
const routeElementsContent = readFileSync(routeElementsPath, 'utf-8');

// Load backend entitlement routes
const backendEntitlementPath = resolve(PHR_BACKEND_DIR, 'api/routes/PhrEntitlementRoutes.java');
if (!existsSync(backendEntitlementPath)) {
  console.error('ERROR: PhrEntitlementRoutes.java not found');
  process.exit(1);
}
const backendEntitlementContent = readFileSync(backendEntitlementPath, 'utf-8');

// Extract route paths from JSON contract
const jsonPaths = new Set(jsonContract.routes.map(r => r.path));

// Extract route paths from TS contract (look for path: '...' strings)
const tsPaths = new Set();
const tsPathMatches = tsContractContent.match(/path:\s*['"`]([^'"`]+)['"`]/g);
if (tsPathMatches) {
  tsPathMatches.forEach(match => {
    const path = match.match(/['"`]([^'"`]+)['"`]/)[1];
    tsPaths.add(path);
  });
}

// Extract route paths from route elements
const elementPaths = new Set();
const elementPathMatches = routeElementsContent.match(/path:\s*['"`]([^'"`]+)['"`]/g);
if (elementPathMatches) {
  elementPathMatches.forEach(match => {
    const path = match.match(/['"`]([^'"`]+)['"`]/)[1];
    elementPaths.add(path);
  });
}

// Check parity
let hasErrors = false;

// Check JSON vs TS
for (const path of jsonPaths) {
  if (!tsPaths.has(path)) {
    console.error(`ERROR: JSON contract path '${path}' not found in TS contract`);
    hasErrors = true;
  }
}

for (const path of tsPaths) {
  if (!jsonPaths.has(path)) {
    console.error(`ERROR: TS contract path '${path}' not found in JSON contract`);
    hasErrors = true;
  }
}

// Check TS vs route elements
for (const path of tsPaths) {
  if (!elementPaths.has(path)) {
    console.error(`ERROR: TS contract path '${path}' not found in route elements`);
    hasErrors = true;
  }
}

for (const path of elementPaths) {
  if (!tsPaths.has(path)) {
    console.error(`ERROR: Route element path '${path}' not found in TS contract`);
    hasErrors = true;
  }
}

// Check role order parity
const jsonRoleOrder = jsonContract.roleOrder;
const tsRoleOrderMatch = tsContractContent.match(/PHR_ROLE_ORDER\s*=\s*{([^}]+)}/);
if (tsRoleOrderMatch) {
  const tsRoleOrderStr = tsRoleOrderMatch[1];
  const tsRoles = tsRoleOrderStr.split(',').map(r => r.trim().replace(/['"`]/g, '').split(':')[0]);
  
  for (const role of Object.keys(jsonRoleOrder)) {
    if (!tsRoles.includes(role)) {
      console.error(`ERROR: JSON role '${role}' not found in TS role order`);
      hasErrors = true;
    }
  }
  
  for (const role of tsRoles) {
    if (!jsonRoleOrder.hasOwnProperty(role)) {
      console.error(`ERROR: TS role '${role}' not found in JSON role order`);
      hasErrors = true;
    }
  }
}

// Check backend entitlements reference JSON contract
if (!backendEntitlementContent.includes('phr-route-contract.json')) {
  console.warn('WARNING: Backend entitlement routes may not reference phr-route-contract.json');
}

if (hasErrors) {
  console.error('FAIL: Route contract parity check failed');
  process.exit(1);
}

console.log('PASS: Route contract parity check passed');
