#!/usr/bin/env node

/**
 * PHR Route State Enforcement Gate
 *
 * The PHR route contract no longer uses route-level feature flags. Visibility
 * is derived from the canonical JSON route state: stable, preview, hidden, or
 * blocked. This gate keeps the legacy flag path out of the web projection and
 * verifies that hidden/blocked direct links are denied by the route renderer.
 *
 * Exit code 0 = no violations.
 * Exit code 1 = one or more violations found.
 */

import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');

const ROUTE_CONTRACT_FILE = join(REPO_ROOT, 'products/phr/config/phr-route-contract.json');
const ROUTE_PROJECTION_FILE = join(REPO_ROOT, 'products/phr/apps/web/src/phrRouteContracts.ts');
const ROUTE_ELEMENTS_FILE = join(REPO_ROOT, 'products/phr/apps/web/src/phrRouteElements.tsx');
const LEGACY_VISIBILITY_FILES = [
  join(REPO_ROOT, 'products/phr/config/feature-visibility.json'),
  join(REPO_ROOT, 'products/phr/config/phr-feature-visibility.json'),
];

function readUtf8(path) {
  return readFileSync(path, 'utf-8');
}

function main() {
  console.log('[phr-route-state] Checking canonical route-state enforcement...');

  const violations = [];
  const routeContract = JSON.parse(readUtf8(ROUTE_CONTRACT_FILE));
  const routeProjection = readUtf8(ROUTE_PROJECTION_FILE);
  const routeElements = readUtf8(ROUTE_ELEMENTS_FILE);

  for (const legacyPath of LEGACY_VISIBILITY_FILES) {
    if (existsSync(legacyPath)) {
      violations.push(`${legacyPath}: remove legacy route visibility config; use phr-route-contract.json`);
    }
  }

  if (!Array.isArray(routeContract.routes)) {
    violations.push('phr-route-contract.json must contain a routes array');
  }

  for (const route of routeContract.routes ?? []) {
    if (Object.prototype.hasOwnProperty.call(route, 'featureFlag')) {
      violations.push(`${route.path}: remove legacy featureFlag metadata from canonical contract`);
    }
  }

  if (routeProjection.includes('featureFlag')) {
    violations.push('phrRouteContracts.ts must not expose legacy featureFlag route metadata');
  }

  if (routeElements.includes('FeatureFlagPage')) {
    violations.push('phrRouteElements.tsx must not render FeatureFlagPage for blocked or hidden routes');
  }

  const hiddenGuard =
    routeElements.includes("route.stability === 'hidden'") && routeElements.includes('<NotFoundPage />');
  if (!hiddenGuard) {
    violations.push('hidden routes must render NotFoundPage on direct link');
  }

  const blockedGuard =
    routeElements.includes("route.stability === 'blocked'") && routeElements.includes('<ForbiddenPage />');
  if (!blockedGuard) {
    violations.push('blocked routes must render ForbiddenPage on direct link');
  }

  if (violations.length > 0) {
    console.error('[phr-route-state] FAIL: route-state violations detected:');
    for (const violation of violations) {
      console.error(`  - ${violation}`);
    }
    process.exit(1);
  }

  console.log('[phr-route-state] PASS: route states are enforced from the canonical contract.');
}

main();
