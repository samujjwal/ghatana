#!/usr/bin/env node

/**
 * Checks PHR route contract parity across the canonical JSON contract, frontend
 * projection, route elements, and backend entitlement loader.
 */

import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

const PHR_CONFIG_DIR = resolve(process.cwd(), 'products/phr/config');
const PHR_WEB_SRC_DIR = resolve(process.cwd(), 'products/phr/apps/web/src');
const PHR_BACKEND_DIR = resolve(process.cwd(), 'products/phr/src/main/java/com/ghatana/phr');

function readRequired(path, label) {
  if (!existsSync(path)) {
    console.error(`ERROR: ${label} not found`);
    process.exit(1);
  }
  return readFileSync(path, 'utf-8');
}

const jsonContractPath = resolve(PHR_CONFIG_DIR, 'phr-route-contract.json');
const jsonContract = JSON.parse(readRequired(jsonContractPath, 'phr-route-contract.json'));
const tsContractContent = readRequired(
  resolve(PHR_WEB_SRC_DIR, 'phrRouteContracts.ts'),
  'phrRouteContracts.ts'
);
const routeElementsContent = readRequired(
  resolve(PHR_WEB_SRC_DIR, 'phrRouteElements.tsx'),
  'phrRouteElements.tsx'
);
const routePluginContent = readRequired(
  resolve(PHR_WEB_SRC_DIR, 'phrRoutePlugin.ts'),
  'phrRoutePlugin.ts'
);
const routesContent = readRequired(
  resolve(PHR_WEB_SRC_DIR, 'routes.tsx'),
  'routes.tsx'
);
const backendEntitlementContent = readRequired(
  resolve(PHR_BACKEND_DIR, 'api/routes/PhrEntitlementRoutes.java'),
  'PhrEntitlementRoutes.java'
);

const jsonPaths = new Set(jsonContract.routes.map((route) => route.path));
const stableRoutes = jsonContract.routes.filter((route) => route.stability === 'stable');
const elementPaths = new Set();
const elementPathMatches = routeElementsContent.match(/['"`](\/[^'"`]+)['"`]\s*:/g) ?? [];

for (const match of elementPathMatches) {
  elementPaths.add(match.match(/['"`]([^'"`]+)['"`]/)[1]);
}

let hasErrors = false;

if (!tsContractContent.includes("../../../config/phr-route-contract.json")) {
  console.error('ERROR: phrRouteContracts.ts must import the canonical JSON route contract');
  hasErrors = true;
}

if (/path:\s*['"`]\//.test(tsContractContent)) {
  console.error('ERROR: phrRouteContracts.ts contains hand-maintained route path literals');
  hasErrors = true;
}

if (!routePluginContent.includes('createRouteContractGenerator(routeContractJson)')) {
  console.error('ERROR: phrRoutePlugin.ts must use the Kernel route contract generator');
  hasErrors = true;
}

if (!/phrRoutePlugin\s*\.getBrowserRoutes\(\)/.test(routesContent)) {
  console.error('ERROR: routes.tsx must register browser routes through phrRoutePlugin.getBrowserRoutes()');
  hasErrors = true;
}

if (!routeElementsContent.includes('phrRoutePlugin.isBrowserMountable(route)')) {
  console.error('ERROR: phrRouteElements.tsx must delegate browser visibility to the PHR route plugin');
  hasErrors = true;
}

if (jsonPaths.has('/mobile/dashboard')) {
  console.error('ERROR: /mobile/dashboard must not be part of the web route contract');
  hasErrors = true;
}

for (const route of stableRoutes) {
  for (const field of ['apiEndpoint', 'policyId', 'testId']) {
    if (!route[field] || typeof route[field] !== 'string') {
      console.error(`ERROR: stable route '${route.path}' is missing ${field}`);
      hasErrors = true;
    }
  }
}

for (const route of stableRoutes) {
  if (!elementPaths.has(route.path)) {
    console.error(`ERROR: stable JSON contract path '${route.path}' not found in route elements`);
    hasErrors = true;
  }
}

for (const path of elementPaths) {
  if (!jsonPaths.has(path)) {
    console.error(`ERROR: route element path '${path}' not found in JSON contract`);
    hasErrors = true;
  }
}

if (!routeElementsContent.includes("route.stability === 'hidden'")) {
  console.error('ERROR: phrRouteElements.tsx must explicitly block hidden direct links');
  hasErrors = true;
}

for (const role of ['patient', 'caregiver', 'clinician', 'fchv', 'admin']) {
  if (!Object.prototype.hasOwnProperty.call(jsonContract.roleOrder ?? {}, role)) {
    console.error(`ERROR: JSON role order missing '${role}'`);
    hasErrors = true;
  }
}

if (!backendEntitlementContent.includes('phr-route-contract.json')) {
  console.error('ERROR: backend entitlement routes must reference phr-route-contract.json');
  hasErrors = true;
}

if (hasErrors) {
  console.error('FAIL: Route contract parity check failed');
  process.exit(1);
}

console.log('PASS: Route contract parity check passed');
