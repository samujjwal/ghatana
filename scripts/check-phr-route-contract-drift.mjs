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

import { existsSync, readFileSync, readdirSync } from 'fs';
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
const baselineRaw = readRequired(resolve(CONFIG_DIR, 'phr-usecase-baseline.json'), 'phr-usecase-baseline.json');
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

let baseline;
try {
  baseline = JSON.parse(baselineRaw);
} catch (err) {
  console.error(`ERROR [drift]: phr-usecase-baseline.json is not valid JSON: ${err.message}`);
  process.exit(1);
}

const allRoutes = jsonContract.routes ?? [];
const stableRoutes = allRoutes.filter((r) => r.stability === 'stable');
const suppressedNotFoundRoutes = allRoutes.filter((r) => ['hidden', 'deferred', 'removed'].includes(r.stability));
const suppressedForbiddenRoutes = allRoutes.filter((r) => ['blocked', 'preview'].includes(r.stability));
const roleOrder = jsonContract.roleOrder ?? {};
const baselineUseCases = baseline.useCases ?? [];

// Create a map from route path to baseline status
const baselineStatusByRoute = new Map();
for (const useCase of baselineUseCases) {
  if (useCase.webRoute) {
    baselineStatusByRoute.set(useCase.webRoute, useCase.status);
  }
}

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
  for (const field of [
    'apiEndpoint',
    'policyId',
    'testId',
    'apiContractId',
    'dtoSchemaId',
    'pluginDependencies',
    'auditRequirement',
    'phiSensitivity',
    'cachePolicy',
    'offlinePolicy',
  ]) {
    if (!route[field] || typeof route[field] !== 'string' || route[field].trim() === '') {
      if (!Array.isArray(route[field]) || route[field].length === 0) {
        fail(`stable route '${route.path}' is missing required field '${field}'`);
      }
    }
  }
}
pass(`stable routes have required metadata fields (${stableRoutes.length} routes)`);

// ROUTE-02: Align baseline status with route contract stability
let baselineMisalignments = 0;
const stabilityToBaselineStatus = {
  'stable': 'implemented',
  'preview': 'partial',
  'hidden': 'deferred',
  'deferred': 'deferred',
  'removed': 'deferred',
  'blocked': 'blocked'
};

for (const route of allRoutes) {
  const baselineStatus = baselineStatusByRoute.get(route.path);
  if (!baselineStatus) {
    continue; // Route may not have a baseline entry
  }

  const expectedStatus = stabilityToBaselineStatus[route.stability];
  if (expectedStatus && baselineStatus !== expectedStatus) {
    baselineMisalignments++;
    fail(`route '${route.path}' has stability '${route.stability}' but baseline status is '${baselineStatus}' (expected '${expectedStatus}')`);
  }
}
if (baselineMisalignments === 0) {
  pass(`baseline status aligns with route contract stability`);
}

// 2 & 3. Route elements bijection: browser-mountable JSON routes ↔ phrRouteElements.tsx
const elementPathMatches = [...routeElements.matchAll(/^\s+'(\/[^']+)'\s*:/gm)].map((m) => m[1]);
const elementPaths = new Set(elementPathMatches);
const jsonPaths = new Set(allRoutes.map((r) => r.path));
const hasWebSurface = (route) => Array.isArray(route.surface) && route.surface.includes('web');
const browserMountableRoutes = allRoutes.filter((r) => hasWebSurface(r) && ['stable', 'preview', 'blocked'].includes(r.stability));
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

// 11. Clinical routes must have minimumRole 'clinician' not 'caregiver'
const clinicalRoutes = allRoutes.filter((r) => r.group === 'clinical');
for (const route of clinicalRoutes) {
  if (route.minimumRole === 'caregiver' && route.personas && route.personas.includes('clinician')) {
    fail(`clinical route '${route.path}' has minimumRole 'caregiver' but includes 'clinician' in personas - should be 'clinician'`);
  }
}
pass(`clinical routes have correct minimumRole for clinical data access (${clinicalRoutes.length} routes)`);

// 12. Check baseline status alignment with route contract stability
const baselinePath = resolve(CONFIG_DIR, 'phr-usecase-baseline.json');
if (existsSync(baselinePath)) {
  const baselineRaw = readFileSync(baselinePath, 'utf-8');
  let baseline;
  try {
    baseline = JSON.parse(baselineRaw);
  } catch (err) {
    fail(`phr-usecase-baseline.json is not valid JSON: ${err.message}`);
  }
  
  if (baseline && baseline.usecases) {
    const stabilityToBaseline = {
      stable: 'implemented',
      preview: 'partial',
      blocked: 'deferred',
      hidden: 'deferred',
      deferred: 'deferred',
      removed: 'removed'
    };
    
    const routeStabilityMap = new Map(allRoutes.map(r => [r.path, r.stability]));
    let baselineMisalignments = 0;
    
    for (const usecase of baseline.usecases) {
      const routePath = usecase.iaRoute || usecase.webRoute;
      if (!routePath) continue;
      
      const stability = routeStabilityMap.get(routePath);
      if (!stability) continue;
      
      const expectedStatus = stabilityToBaseline[stability];
      if (usecase.status !== expectedStatus) {
        baselineMisalignments++;
        fail(`baseline usecase '${usecase.id}' (${routePath}) status '${usecase.status}' does not match route stability '${stability}' (expected '${expectedStatus}')`);
      }
    }
    
    if (baselineMisalignments === 0) {
      pass(`baseline status aligns with route contract stability (${baseline.usecases.length} usecases)`);
    }
  }
} else {
  pass(`baseline file not found, skipping baseline alignment check`);
}

// 13. Stable page routes must have corresponding page components
const pagesDir = resolve(WEB_SRC, 'pages');
if (existsSync(pagesDir)) {
  const pageFiles = readdirSync(pagesDir).filter(f => f.endsWith('.tsx') && !f.endsWith('.test.tsx'));
  const pageComponentNames = new Set(pageFiles.map(f => f.replace('.tsx', '')));
  
  const stablePageRoutes = stableRoutes.filter(r => r.routeType === 'page' && r.surface && r.surface.includes('web'));
  let missingComponents = 0;
  
  for (const route of stablePageRoutes) {
    let expectedComponentName = route.path.split('/').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join('');
    if (expectedComponentName === 'Consents') expectedComponentName = 'Consent';
    expectedComponentName += 'Page';
    if (route.path === '/release-readiness') expectedComponentName = 'ReleaseCockpitPage';
    
    if (!pageComponentNames.has(expectedComponentName)) {
      missingComponents++;
      fail(`stable page route '${route.path}' is missing page component: ${expectedComponentName}.tsx`);
    }
  }
  
  if (missingComponents === 0) {
    pass(`stable page routes have corresponding page components (${stablePageRoutes.length} routes)`);
  }
} else {
  pass(`pages directory not found, skipping page component check`);
}

// 14. Backend mount table must cover all stable backend routes
const backendMountTablePath = resolve(BACKEND_ROUTES, '../PhrRouteContractMountTable.java');
if (existsSync(backendMountTablePath)) {
  const mountTableRaw = readFileSync(backendMountTablePath, 'utf-8');
  const stableBackendRoutes = stableRoutes.filter(r => r.surface && r.surface.includes('backend'));
  
  let missingMounts = 0;
  for (const route of stableBackendRoutes) {
    if (!route.apiEndpoint) continue;
    // Check if the endpoint is covered in the mount table logic
    // The mount table uses wildcard patterns, so we check if the endpoint path prefix is covered
    const endpoint = route.apiEndpoint.replace(/:\w+/g, ':id');
    const endpointParts = endpoint.split('/');
    
    // Check for exact match or wildcard coverage
    let isCovered = mountTableRaw.includes(endpoint);
    
    // If not exact match, check if a parent path has a wildcard mount
    if (!isCovered) {
      for (let i = endpointParts.length; i > 0; i--) {
        const prefix = endpointParts.slice(0, i).join('/');
        if (mountTableRaw.includes(`${prefix}/*`)) {
          isCovered = true;
          break;
        }
      }
    }
    
    if (!isCovered) {
      missingMounts++;
      fail(`stable backend route '${route.path}' endpoint '${route.apiEndpoint}' may not be covered in mount table`);
    }
  }
  
  if (missingMounts === 0) {
    pass(`backend mount table covers all stable backend routes (${stableBackendRoutes.length} routes)`);
  }
} else {
  pass(`backend mount table file not found, skipping mount table check`);
}

// 15. Mobile routes must align with contract mobile surface
const mobileSrc = resolve(ROOT, 'products/phr/apps/mobile/src');
const mobileRouteManifestPath = resolve(mobileSrc, 'mobileRouteManifest.ts');
if (existsSync(mobileRouteManifestPath)) {
  const mobileRouteManifestRaw = readFileSync(mobileRouteManifestPath, 'utf-8');
  const stableMobileRoutes = stableRoutes.filter(r => r.surface && r.surface.includes('mobile'));
  
  // Extract route paths from mobile manifest
  const mobilePathMatches = [...mobileRouteManifestRaw.matchAll(/path:\s*['"`](\/[^'"`]+)['"`]/g)].map(m => m[1]);
  const mobilePaths = new Set(mobilePathMatches);
  
  let missingMobileRoutes = 0;
  for (const route of stableMobileRoutes) {
    if (!mobilePaths.has(route.path)) {
      missingMobileRoutes++;
      fail(`stable mobile route '${route.path}' is missing from mobile route manifest`);
    }
  }
  
  if (missingMobileRoutes === 0) {
    pass(`mobile route manifest covers all stable mobile routes (${stableMobileRoutes.length} routes)`);
  }
} else {
  pass(`mobile route manifest not found, skipping mobile route check`);
}

// 16. Test files must exist for stable routes
const webTestsDir = resolve(WEB_SRC, '__tests__');
const backendTestsDir = resolve(BACKEND_ROUTES, '../test/java/com/ghatana/phr/api/routes');
let missingTests = 0;

if (existsSync(webTestsDir)) {
  const webTestFiles = readdirSync(webTestsDir).filter(f => f.endsWith('.test.tsx') || f.endsWith('.test.ts'));
  const webTestNames = new Set(webTestFiles.map(f => f.replace(/\.(test\.)?(tsx|ts)$/, '')));
  
  for (const route of stableRoutes.filter(r => r.surface && r.surface.includes('web'))) {
    let expectedTestName = route.path.split('/').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join('');
    if (expectedTestName === 'Consents') expectedTestName = 'Consent';
    if (route.path === '/release-readiness') continue; // System page may not need test
    
    if (!webTestNames.has(expectedTestName)) {
      missingTests++;
      fail(`stable web route '${route.path}' is missing test file: ${expectedTestName}.test.tsx`);
    }
  }
}

if (existsSync(backendTestsDir)) {
  const backendTestFiles = readdirSync(backendTestsDir).filter(f => f.endsWith('Test.java'));
  const backendTestNames = new Set(backendTestFiles.map(f => f.replace('Test.java', '').replace('Phr', '').toLowerCase()));
  
  for (const route of stableRoutes.filter(r => r.surface && r.surface.includes('backend'))) {
    const routeName = route.path.split('/').pop();
    if (routeName && !backendTestNames.has(routeName.toLowerCase())) {
      // Not all backend routes need separate test files, so this is informational
      console.log(`INFO [drift]: backend route '${route.path}' may not have dedicated test file`);
    }
  }
}

if (missingTests === 0) {
  pass(`stable web routes have corresponding test files`);
}

// 17. i18n keys must exist in translation files
const i18nDir = resolve(WEB_SRC, 'i18n');
if (existsSync(i18nDir)) {
  const i18nFiles = readdirSync(i18nDir).filter(f => f.endsWith('.json'));
  let missingI18nKeys = 0;
  
  for (const i18nFile of i18nFiles) {
    const i18nRaw = readFileSync(resolve(i18nDir, i18nFile), 'utf-8');
    let i18nData;
    try {
      i18nData = JSON.parse(i18nRaw);
    } catch (err) {
      fail(`i18n file ${i18nFile} is not valid JSON: ${err.message}`);
      continue;
    }
    
    for (const route of stableRoutes.filter(r => r.surface && r.surface.includes('web'))) {
      if (route.i18nKey && !getNestedValue(i18nData, route.i18nKey)) {
        missingI18nKeys++;
        fail(`stable route '${route.path}' i18nKey '${route.i18nKey}' not found in ${i18nFile}`);
      }
      if (route.descriptionI18nKey && !getNestedValue(i18nData, route.descriptionI18nKey)) {
        missingI18nKeys++;
        fail(`stable route '${route.path}' descriptionI18nKey '${route.descriptionI18nKey}' not found in ${i18nFile}`);
      }
    }
  }
  
  if (missingI18nKeys === 0) {
    pass(`stable route i18n keys exist in translation files (${i18nFiles.length} files)`);
  }
} else {
  pass(`i18n directory not found, skipping i18n key check`);
}

function getNestedValue(obj, path) {
  return path.split('.').reduce((current, key) => current && current[key], obj);
}

// Summary
if (failures > 0) {
  console.error(`\nFAIL: ${failures} route contract drift violation(s) found`);
  process.exit(1);
}

console.log(`\nPASS: Route contract drift check passed (${allRoutes.length} routes, ${stableRoutes.length} stable, ${suppressedNotFoundRoutes.length} not-found suppressed, ${suppressedForbiddenRoutes.length} forbidden suppressed)`);
