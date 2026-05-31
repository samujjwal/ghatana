#!/usr/bin/env node

/**
 * G11-002: PHR route contract drift check.
 *
 * Verifies that the canonical phr-route-contract.json is the single source
 * of truth and that all downstream representations (TS route elements, web router,
 * backend entitlement loader) remain in sync and enforce non-stable route
 * lifecycles correctly. This is a CI-safe static analysis check; no runtime calls needed.
 *
 * Assertions:
 *   1. Every stable route in the JSON contract has apiEndpoint, policyId, testId.
 *   2. Every browser-mountable route in the JSON contract has a corresponding element in phrRouteElements.tsx.
 *   3. No route element exists in phrRouteElements.tsx that is absent from the JSON contract.
 *   4. Hidden/deferred/removed routes redirect to /not-found in routes.tsx guard.
 *   5. Blocked/preview routes redirect to /forbidden in routes.tsx guard.
 *   6. phrRouteContracts.ts imports from the canonical JSON file and does not duplicate paths.
 *   7. Backend PhrEntitlementRoutes includes stable routes only in entitlement output.
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
const suppressedNotFoundRoutes = allRoutes.filter((r) => ['hidden', 'deferred', 'removed'].includes(r.stability));
const suppressedForbiddenRoutes = allRoutes.filter((r) => ['blocked', 'preview'].includes(r.stability));
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

// 2 & 3. Route elements bijection: browser-mountable JSON routes ↔ phrRouteElements.tsx
const elementPathMatches = [...routeElements.matchAll(/^\s+'(\/[^']+)'\s*:/gm)].map((m) => m[1]);
const elementPaths = new Set(elementPathMatches);
const jsonPaths = new Set(allRoutes.map((r) => r.path));
const browserMountableRoutes = allRoutes.filter((r) => ['stable', 'preview', 'blocked'].includes(r.stability));
const browserMountablePaths = new Set(browserMountableRoutes.map((r) => r.path));

for (const route of browserMountableRoutes) {
  if (!elementPaths.has(route.path)) {
    fail(`JSON contract path '${route.path}' has no entry in phrRouteElements.tsx routeElements map`);
  }
}

for (const path of elementPaths) {
  if (!browserMountablePaths.has(path)) {
    fail(`phrRouteElements.tsx has element for '${path}' which is absent from phr-route-contract.json`);
  }
}
pass(`phrRouteElements.tsx ↔ browser-mountable JSON contract bijection verified (${browserMountableRoutes.length} routes)`);

for (const route of suppressedNotFoundRoutes) {
  if (elementPaths.has(route.path)) {
    fail(`suppressed route '${route.path}' must not have a product page element`);
  }
}
pass(`hidden/deferred/removed routes have no product page elements (${suppressedNotFoundRoutes.length} routes)`);

// 4. Hidden/deferred/removed routes must redirect to /not-found in routes.tsx, not render element.
const notFoundGuardStates = ['hidden', 'deferred', 'removed'];
if (!notFoundGuardStates.every((state) => routesTsx.includes(`route.stability === '${state}'`)) || !routesTsx.includes('/not-found')) {
  fail(`routes.tsx must redirect hidden/deferred/removed routes to /not-found, not render their element`);
} else {
  pass(`hidden/deferred/removed routes redirect to /not-found in routes.tsx`);
}

// 5. Blocked/preview routes must redirect to /forbidden in routes.tsx.
const forbiddenGuardStates = ['blocked', 'preview'];
if (!forbiddenGuardStates.every((state) => routesTsx.includes(`route.stability === '${state}'`)) || !routesTsx.includes('/forbidden')) {
  fail(`routes.tsx must redirect blocked/preview routes to /forbidden`);
} else {
  pass(`blocked/preview routes redirect to /forbidden in routes.tsx`);
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

// 7. Backend entitlement routes must publish only stable entries.
const stableOnlyGuard = entitlementRoutes.match(/if\s*\(\s*!\s*["']stable["']\.equals\(stability\)\s*\)\s*\{[\s\S]{0,120}continue\s*;/);
if (!stableOnlyGuard) {
  fail(`PhrEntitlementRoutes.java must exclude every non-stable route from entitlement output`);
} else {
  pass(`backend PhrEntitlementRoutes publishes stable routes only`);
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

console.log(`\nPASS: Route contract drift check passed (${allRoutes.length} routes, ${stableRoutes.length} stable, ${suppressedNotFoundRoutes.length} not-found suppressed, ${suppressedForbiddenRoutes.length} forbidden suppressed)`);
