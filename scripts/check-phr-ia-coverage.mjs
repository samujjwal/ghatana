#!/usr/bin/env node
/**
 * PHR IA Coverage Gate
 * --------------------
 * Validates that every route defined in the PHR IA baseline has a corresponding
 * entry in the canonical route contract and, for stable routes, also an element
 * registered in `phrRouteElements`.
 *
 * Reads the canonical IA baseline from `products/phr/config/phr-usecase-baseline.json`
 * instead of hardcoded routes.
 *
 * Exit code 0 = all IA routes are covered.
 * Exit code 1 = one or more IA routes are missing from the route manifest.
 *
 * Usage:
 *   node scripts/check-phr-ia-coverage.mjs
 *
 * Can also be imported as an ES module by other scripts.
 */

import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname as pathDirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));

// ---------------------------------------------------------------------------
// Load canonical IA baseline
// ---------------------------------------------------------------------------
const BASELINE_FILE = resolve(
  __dirname,
  '../products/phr/config/phr-usecase-baseline.json',
);

let baseline;
try {
  const baselineSource = readFileSync(BASELINE_FILE, 'utf8');
  baseline = JSON.parse(baselineSource);
} catch (err) {
  console.error(`[phr-ia-coverage] Cannot read IA baseline file: ${BASELINE_FILE}`);
  console.error(err.message);
  process.exit(1);
}

// Extract IA-declared routes from baseline
// Include all routes regardless of status - they must be in the contract
const IA_DECLARED_ROUTES = baseline.usecases
  .filter(uc => uc.iaRoute && uc.iaRoute !== 'null')
  .flatMap(uc => {
    // Handle comma-separated routes like "/forbidden, /not-found"
    if (uc.iaRoute.includes(',')) {
      return uc.iaRoute.split(',').map(r => r.trim());
    }
    return [uc.iaRoute];
  })
  // Normalize IA routes to React Router style for comparison
  .map(route => route.replace(/\{([^}]+)\}/g, ':$1'))
  // Filter out mobile-only and backend-only routes for web contract check
  .filter(route => !route.startsWith('/mobile/'))
  .sort();

// ---------------------------------------------------------------------------
// Load the canonical route contract.
// ---------------------------------------------------------------------------
const CONTRACT_FILE = resolve(
  __dirname,
  '../products/phr/config/phr-route-contract.json',
);

let routeContract;
try {
  routeContract = JSON.parse(readFileSync(CONTRACT_FILE, 'utf8'));
} catch (err) {
  console.error(`[phr-ia-coverage] Cannot read route contract file: ${CONTRACT_FILE}`);
  console.error(err.message);
  process.exit(1);
}

if (!Array.isArray(routeContract.routes)) {
  console.error('[phr-ia-coverage] Route contract must contain a routes array.');
  process.exit(1);
}

const contractRoutes = routeContract.routes;
const contractPaths = new Set(contractRoutes.map(route => route.path));
const useCases = Array.isArray(baseline.usecases) ? baseline.usecases : [];
const useCasesByIaRoute = new Map();
for (const useCase of useCases) {
  if (!useCase.iaRoute || useCase.iaRoute === 'null') {
    continue;
  }
  for (const rawRoute of useCase.iaRoute.split(',').map((route) => route.trim())) {
    if (rawRoute && !rawRoute.startsWith('/mobile/')) {
      useCasesByIaRoute.set(rawRoute.replace(/\{([^}]+)\}/g, ':$1'), useCase);
    }
  }
}

// ---------------------------------------------------------------------------
// Validate
// ---------------------------------------------------------------------------
const missing = [];
const metadataViolations = [];
const evidenceViolations = [];
const mobileSurfaceViolations = [];

for (const iaRoute of IA_DECLARED_ROUTES) {
  if (!contractPaths.has(iaRoute)) {
    missing.push(iaRoute);
  }
}

for (const useCase of useCases) {
  for (const field of ['ownerLayer', 'verificationCommand']) {
    if (typeof useCase[field] !== 'string' || useCase[field].trim() === '') {
      metadataViolations.push(`${useCase.id} is missing ${field}`);
    }
  }
  if (typeof useCase.legacyDeleteRequired !== 'boolean') {
    metadataViolations.push(`${useCase.id} is missing boolean legacyDeleteRequired`);
  }
  if (!Array.isArray(useCase.implementationEvidence)) {
    metadataViolations.push(`${useCase.id} is missing implementationEvidence array`);
    continue;
  }
  if (['implemented', 'partial', 'backend_only', 'ui_only'].includes(useCase.status) && useCase.implementationEvidence.length === 0) {
    evidenceViolations.push(`${useCase.id} has status ${useCase.status} but no implementationEvidence`);
  }
  for (const evidence of useCase.implementationEvidence) {
    if (typeof evidence?.kind !== 'string' || evidence.kind.trim() === '') {
      evidenceViolations.push(`${useCase.id} has evidence without a kind`);
    }
    if (typeof evidence?.file !== 'string' || evidence.file.trim() === '') {
      evidenceViolations.push(`${useCase.id} has evidence without a file`);
      continue;
    }
    if (!existsSync(resolve(__dirname, '..', evidence.file))) {
      evidenceViolations.push(`${useCase.id} evidence file does not exist: ${evidence.file}`);
    }
  }
}

const MOBILE_SCREEN_FILE_BY_NAME = new Map([
  ['dashboard', 'DashboardScreen.tsx'],
  ['records', 'RecordsScreen.tsx'],
  ['record-detail', 'RecordDetailScreen.tsx'],
  ['consents', 'ConsentScreen.tsx'],
  ['notifications', 'NotificationsScreen.tsx'],
  ['emergency', 'EmergencyAccessScreen.tsx'],
  ['settings', 'SettingsScreen.tsx'],
]);

for (const useCase of useCases) {
  if (!useCase.mobileScreen) {
    continue;
  }
  const mobileFile = MOBILE_SCREEN_FILE_BY_NAME.get(useCase.mobileScreen);
  if (!mobileFile) {
    mobileSurfaceViolations.push(`${useCase.id} declares unknown mobileScreen '${useCase.mobileScreen}'`);
    continue;
  }
  const mobilePath = resolve(__dirname, '..', 'products/phr/apps/mobile/src/screens', mobileFile);
  if (!existsSync(mobilePath)) {
    mobileSurfaceViolations.push(`${useCase.id} mobileScreen '${useCase.mobileScreen}' does not resolve to ${mobileFile}`);
  }
}

for (const route of contractRoutes) {
  if (!Array.isArray(route.surface) || !route.surface.includes('mobile')) {
    continue;
  }
  const useCase = useCasesByIaRoute.get(route.path);
  if (!useCase?.mobileScreen) {
    mobileSurfaceViolations.push(`route ${route.path} declares mobile surface but no baseline use case owns a mobileScreen`);
  }
}

if (missing.length > 0) {
  console.error('\n[phr-ia-coverage] FAIL: The following IA-declared routes are not in the canonical route contract:\n');
  for (const route of missing) {
    console.error(`  \u2717  ${route}`);
  }
  console.error(
    '\nAdd these routes to products/phr/config/phr-route-contract.json with appropriate stability and role metadata.\n',
  );
  process.exit(1);
}

if (metadataViolations.length > 0 || evidenceViolations.length > 0 || mobileSurfaceViolations.length > 0) {
  if (metadataViolations.length > 0) {
    console.error('\n[phr-ia-coverage] FAIL: Baseline metadata violations:\n');
    for (const violation of metadataViolations) {
      console.error(`  \u2717  ${violation}`);
    }
  }
  if (evidenceViolations.length > 0) {
    console.error('\n[phr-ia-coverage] FAIL: Baseline evidence violations:\n');
    for (const violation of evidenceViolations) {
      console.error(`  \u2717  ${violation}`);
    }
  }
  if (mobileSurfaceViolations.length > 0) {
    console.error('\n[phr-ia-coverage] FAIL: Mobile surface parity violations:\n');
    for (const violation of mobileSurfaceViolations) {
      console.error(`  \u2717  ${violation}`);
    }
  }
  process.exit(1);
}

console.log(
  `[phr-ia-coverage] PASS: All ${IA_DECLARED_ROUTES.length} IA-declared routes are present in the canonical route contract.`,
);
console.log('[phr-ia-coverage] PASS: Use-case baseline metadata, evidence files, and mobile surfaces are truthful.');

// ---------------------------------------------------------------------------
// Phase 2: verify stable routes have registered page components
// ---------------------------------------------------------------------------

const ELEMENTS_FILE = resolve(
  __dirname,
  '../products/phr/apps/web/src/phrRouteElements.tsx',
);

let elementsSource;
try {
  elementsSource = readFileSync(ELEMENTS_FILE, 'utf8');
} catch (err) {
  console.error(`[phr-ia-coverage] Cannot read route elements file: ${ELEMENTS_FILE}`);
  console.error(err.message);
  process.exit(1);
}

// Extract paths registered in phrRouteElements.tsx.
// Paths appear either as `path: '/...'` properties or as object keys `'/...':`.
const ELEMENTS_PATH_RE = /(?:path:\s*|^\s*)['"](\/?[^'"]+)['"]\s*:/gm;
const registeredPaths = new Set();
let elemMatch;
while ((elemMatch = ELEMENTS_PATH_RE.exec(elementsSource)) !== null) {
  const candidate = elemMatch[1];
  // Only keep route-path-like strings (start with /)
  if (candidate.startsWith('/')) {
    registeredPaths.add(candidate.replace(/:([^/]+)/g, '{$1}'));
  }
}

const productionRoutesViolations = [];
for (const route of contractRoutes) {
  if (route.stability === 'stable' && Array.isArray(route.surface) && route.surface.includes('web')) {
    const normPath = route.path.replace(/:([^/]+)/g, '{$1}');
    if (!registeredPaths.has(normPath)) {
      productionRoutesViolations.push(route.path);
    }
  }
}

// Verify all relative imports in phrRouteElements.tsx resolve to real files.
const IMPORT_RE = /from\s+['"](\.[^'"]+)['"]/g;
const brokenImports = [];
const elementsDir = pathDirname(ELEMENTS_FILE);
let importMatch;
while ((importMatch = IMPORT_RE.exec(elementsSource)) !== null) {
  const importPath = importMatch[1];
  const candidates = [
    `${importPath}.tsx`,
    `${importPath}.ts`,
    `${importPath}/index.tsx`,
    `${importPath}/index.ts`,
  ].map((c) => resolve(elementsDir, c));
  if (!candidates.some((c) => existsSync(c))) {
    brokenImports.push(importPath);
  }
}

const phase2Failures = productionRoutesViolations.length + brokenImports.length;
if (phase2Failures > 0) {
  if (productionRoutesViolations.length > 0) {
    console.error('\n[phr-ia-coverage] FAIL: Stable routes missing from phrRouteElements.tsx:\n');
    for (const route of productionRoutesViolations) {
      console.error(`  \u2717  ${route}`);
    }
  }
  if (brokenImports.length > 0) {
    console.error('\n[phr-ia-coverage] FAIL: phrRouteElements.tsx imports that do not resolve:\n');
    for (const imp of brokenImports) {
      console.error(`  \u2717  ${imp}`);
    }
  }
  console.error('\n[phr-ia-coverage] Resolve all element-registration and import violations before merging.\n');
  process.exit(1);
}

console.log('[phr-ia-coverage] PASS: All production routes have registered page components and imports resolve.');
process.exit(0);
