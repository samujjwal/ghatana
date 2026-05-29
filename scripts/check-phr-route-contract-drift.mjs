#!/usr/bin/env node

/**
 * G11-002: PHR route contract drift check.
 *
 * Verifies that the canonical phr-route-contract.json is the single source
 * of truth and that all downstream representations (TS route elements, web router,
 * backend entitlement loader) remain in sync and enforce hidden/blocked routes
 * correctly. This is a CI-safe static analysis check — no runtime calls needed.
 *
 * Assertions:
 *   1. Every stable route in the JSON contract has apiEndpoint, policyId, testId.
 *   2. Every route in the JSON contract has a corresponding element in phrRouteElements.tsx.
 *   3. No route element exists in phrRouteElements.tsx that is absent from the JSON contract.
 *   4. Hidden routes redirect to /not-found in routes.tsx guard (not render the real element).
 *   5. Blocked routes redirect to /forbidden in routes.tsx guard (not render the real element).
 *   6. phrRouteContracts.ts imports from the canonical JSON file and does not duplicate paths.
 *   7. Backend PhrEntitlementRoutes skips hidden/blocked routes (does not include them in output).
 *   8. roleOrder in JSON contract contains all five PHR roles.
 *   9. No stable route uses a placeholder policyId pattern (e.g. "phr.TBD").
 *  10. routeManifest.ts re-exports from phrRouteContracts (single import chain).
 */

import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';

const ROOT = process.cwd();
const CONFIG_DIR = resolve(ROOT, 'products/phr/config');
const WEB_SRC = resolve(ROOT, 'products/phr/apps/web/src');
const BACKEND_ROUTES = resolve(ROOT, 'products/phr/src/main/java/com/ghatana/phr/api/routes');

const REQUIRED_PHR_ROLES = ['patient', 'caregiver', 'fchv', 'clinician', 'admin'];

function readRequired(path, label) {
  if (!existsSync(path)) {
    console.error(`ERROR [drift]: ${label} not found at ${path}`);
    process.exit(1);
  }
  return readFileSync(path, 'utf-8');
}

const jsonContractRaw = readRequired(resolve(CONFIG_DIR, 'phr-route-contract.json'), 'phr-route-contract.json');
const tsContracts = readRequired(resolve(WEB_SRC, 'phrRouteContracts.ts'), 'phrRouteContracts.ts');
const routeElements = readRequired(resolve(WEB_SRC, 'phrRouteElements.tsx'), 'phrRouteElements.tsx');
const routesTsx = readRequired(resolve(WEB_SRC, 'routes.tsx'), 'routes.tsx');
const routeManifest = readRequired(resolve(WEB_SRC, 'routeManifest.ts'), 'routeManifest.ts');
const entitlementRoutes = readRequired(resolve(BACKEND_ROUTES, 'PhrEntitlementRoutes.java'), 'PhrEntitlementRoutes.java');

let jsonContract;
try {
  jsonContract = JSON.parse(jsonContractRaw);
} catch (err) {
  console.error(`ERROR [drift]: phr-route-contract.json is not valid JSON: ${err.message}`);
  process.exit(1);
}

const allRoutes = jsonContract.routes ?? [];
const stableRoutes = allRoutes.filter((r) => r.stability === 'stable');
const hiddenRoutes = allRoutes.filter((r) => r.stability === 'hidden');
const blockedRoutes = allRoutes.filter((r) => r.stability === 'blocked');
const roleOrder = jsonContract.roleOrder ?? {};

let failures = 0;

function fail(msg) {
  console.error(`FAIL [drift]: ${msg}`);
  failures++;
}

function pass(msg) {
  console.log(`PASS [drift]: ${msg}`);
}

// 1. Stable routes must have apiEndpoint, policyId, testId
for (const route of stableRoutes) {
  for (const field of ['apiEndpoint', 'policyId', 'testId']) {
    if (!route[field] || typeof route[field] !== 'string' || route[field].trim() === '') {
      fail(`stable route '${route.path}' is missing required field '${field}'`);
    }
  }
}
pass(`stable routes have required metadata fields (${stableRoutes.length} routes)`);

// 2 & 3. Route elements bijection: JSON ↔ phrRouteElements.tsx
const elementPathMatches = [...routeElements.matchAll(/^\s+'(\/[^']+)'\s*:/gm)].map((m) => m[1]);
const elementPaths = new Set(elementPathMatches);
const jsonPaths = new Set(allRoutes.map((r) => r.path));

for (const route of allRoutes) {
  if (!elementPaths.has(route.path)) {
    fail(`JSON contract path '${route.path}' has no entry in phrRouteElements.tsx routeElements map`);
  }
}

for (const path of elementPaths) {
  if (!jsonPaths.has(path)) {
    fail(`phrRouteElements.tsx has element for '${path}' which is absent from phr-route-contract.json`);
  }
}
pass(`phrRouteElements.tsx ↔ JSON contract bijection verified (${allRoutes.length} routes)`);

// 4. Hidden routes must redirect to /not-found in routes.tsx, not render element
if (!routesTsx.includes("route.stability === 'hidden'") || !routesTsx.includes('/not-found')) {
  fail(`routes.tsx must redirect hidden routes to /not-found, not render their element`);
} else {
  // Confirm the guard uses Navigate, not direct element render
  const hiddenBlock = routesTsx.match(/route\.stability === 'hidden'[\s\S]{0,200}/)?.[0] ?? '';
  if (hiddenBlock.includes('return route.element')) {
    fail(`routes.tsx still renders route.element for hidden routes — must use Navigate to /not-found`);
  } else {
    pass(`hidden routes redirect to /not-found in routes.tsx`);
  }
}

// 5. Blocked routes must redirect to /forbidden in routes.tsx
if (!routesTsx.includes("route.stability === 'blocked'") || !routesTsx.includes('/forbidden')) {
  fail(`routes.tsx must redirect blocked routes to /forbidden`);
} else {
  const blockedBlock = routesTsx.match(/route\.stability === 'blocked'[\s\S]{0,200}/)?.[0] ?? '';
  if (blockedBlock.includes('return route.element')) {
    fail(`routes.tsx still renders route.element for blocked routes — must use Navigate to /forbidden`);
  } else {
    pass(`blocked routes redirect to /forbidden in routes.tsx`);
  }
}

// 6. phrRouteContracts.ts imports canonical JSON and does not hardcode paths
if (!tsContracts.includes('../../../config/phr-route-contract.json')) {
  fail(`phrRouteContracts.ts must import from '../../../config/phr-route-contract.json'`);
} else {
  pass(`phrRouteContracts.ts imports canonical JSON contract`);
}

if (/path:\s*['"`]\//.test(tsContracts)) {
  fail(`phrRouteContracts.ts contains hardcoded route path literals — all paths must come from JSON`);
} else {
  pass(`phrRouteContracts.ts has no hardcoded route path literals`);
}

// 7. Backend entitlement routes must skip hidden/blocked entries
// Look for the guard pattern: if ("hidden".equals(stability) || "blocked".equals(stability)) { continue; }
const hiddenBlockedGuard = entitlementRoutes.match(/if\s*\(["']hidden["']\.equals\(stability\)[\s\S]{0,200}continue\s*;/);
if (!hiddenBlockedGuard) {
  fail(`PhrEntitlementRoutes.java must have an if("hidden".equals(stability) || ...) { continue; } guard to exclude hidden/blocked routes from entitlement output`);
} else {
  pass(`backend PhrEntitlementRoutes skips hidden/blocked routes via explicit continue guard`);
}

// 8. Role order must contain all five PHR roles
for (const role of REQUIRED_PHR_ROLES) {
  if (!Object.prototype.hasOwnProperty.call(roleOrder, role)) {
    fail(`phr-route-contract.json roleOrder is missing required role '${role}'`);
  }
}
pass(`roleOrder contains all required PHR roles: ${REQUIRED_PHR_ROLES.join(', ')}`);

// 9. No stable route uses placeholder policyId
for (const route of stableRoutes) {
  if (route.policyId && (route.policyId.includes('TBD') || route.policyId.includes('todo') || route.policyId.includes('PLACEHOLDER'))) {
    fail(`stable route '${route.path}' has a placeholder policyId: '${route.policyId}'`);
  }
}
pass(`no stable route has a placeholder policyId`);

// 10. routeManifest.ts re-exports from phrRouteContracts (not a manual duplicate)
if (!routeManifest.includes('phrRouteContracts')) {
  fail(`routeManifest.ts must re-export from phrRouteContracts to maintain single import chain`);
} else {
  pass(`routeManifest.ts correctly re-exports from phrRouteContracts`);
}

// Summary
if (failures > 0) {
  console.error(`\nFAIL: ${failures} route contract drift violation(s) found`);
  process.exit(1);
}

console.log(`\nPASS: Route contract drift check passed (${allRoutes.length} routes, ${stableRoutes.length} stable, ${hiddenRoutes.length} hidden, ${blockedRoutes.length} blocked)`);
