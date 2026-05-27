#!/usr/bin/env node
/**
 * PHR IA Coverage Gate
 * --------------------
 * Validates that every route defined in the PHR IA documentation (the route
 * contracts) has a corresponding entry in `phrRouteContracts` and — for routes
 * that are not feature-flagged — also an element registered in
 * `phrRouteElements`.
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
import { createRequire } from 'node:module';
import { resolve, dirname as pathDirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const require = createRequire(import.meta.url);

// ---------------------------------------------------------------------------
// IA documentation source of truth
// ---------------------------------------------------------------------------
// These are the routes that the PHR IA documentation declares must exist in
// the product. Paths use OpenAPI-style `{param}` placeholders.
const IA_DECLARED_ROUTES = [
  // Core patient routes
  '/dashboard',
  '/records',
  '/records/{recordId}',
  '/profile',
  '/timeline',
  '/conditions',
  '/observations',
  '/immunizations',
  '/consents',
  '/appointments',
  '/labs',
  '/medications',
  '/documents',
  '/documents/upload',
  '/documents/{docId}/ocr',
  '/notifications',
  '/settings',
  // Error pages
  '/forbidden',
  '/not-found',
  // Governance
  '/emergency',
  '/release-readiness',
  '/audit',
  // Feature-flagged persona routes (must be present in manifest, even if deferred)
  '/provider/dashboard',
  '/provider/patients',
  '/caregiver/dependents',
  '/fchv/dashboard',
];

// ---------------------------------------------------------------------------
// Load the compiled route manifest via ts-node / tsx-compatible path
// ---------------------------------------------------------------------------
// We read the contracts file as text and extract paths with a simple regex
// so this script runs without a TypeScript compilation step.
const CONTRACT_FILE = resolve(
  __dirname,
  '../products/phr/apps/web/src/phrRouteContracts.ts',
);

let contractSource;
try {
  contractSource = readFileSync(CONTRACT_FILE, 'utf8');
} catch (err) {
  console.error(`[phr-ia-coverage] Cannot read route contracts file: ${CONTRACT_FILE}`);
  console.error(err.message);
  process.exit(1);
}

// Extract all path values from the source: path: '/...',
const CONTRACT_PATH_RE = /path:\s*'([^']+)'/g;
const contractPaths = new Set();
let match;
while ((match = CONTRACT_PATH_RE.exec(contractSource)) !== null) {
  // Normalise React Router params (:param) to OpenAPI style ({param}) for comparison
  contractPaths.add(match[1].replace(/:([^/]+)/g, '{$1}'));
}

// ---------------------------------------------------------------------------
// Validate
// ---------------------------------------------------------------------------
const missing = [];

for (const iaRoute of IA_DECLARED_ROUTES) {
  if (!contractPaths.has(iaRoute)) {
    missing.push(iaRoute);
  }
}

if (missing.length > 0) {
  console.error('\n[phr-ia-coverage] FAIL: The following IA-declared routes are not in phrRouteContracts:\n');
  for (const route of missing) {
    console.error(`  \u2717  ${route}`);
  }
  console.error(
    '\nAdd these routes to phrRouteContracts.ts with an appropriate featureFlag or minimumRole entry.\n',
  );
  process.exit(1);
}

console.log(
  `[phr-ia-coverage] PASS: All ${IA_DECLARED_ROUTES.length} IA-declared routes are present in phrRouteContracts.`,
);

// ---------------------------------------------------------------------------
// Phase 2: verify non-feature-flagged routes have registered page components
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

// Identify non-feature-flagged routes from the contracts source.
// A route is feature-flagged when `featureFlag: true` appears in the same object block.
const BLOCK_RE = /\{[^{}]*path:\s*'([^']+)'[^{}]*/g;
const productionRoutesViolations = [];
let blockMatch;
while ((blockMatch = BLOCK_RE.exec(contractSource)) !== null) {
  const block = blockMatch[0];
  const path = blockMatch[1];
  const isFeatureFlagged = /featureFlag:\s*true/.test(block);
  if (!isFeatureFlagged) {
    const normPath = path.replace(/:([^/]+)/g, '{$1}');
    if (!registeredPaths.has(normPath)) {
      productionRoutesViolations.push(path);
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
    console.error('\n[phr-ia-coverage] FAIL: Non-feature-flagged routes missing from phrRouteElements.tsx:\n');
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
