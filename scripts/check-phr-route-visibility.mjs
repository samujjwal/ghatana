#!/usr/bin/env node

/**
 * PHR Route Visibility Check
 *
 * Verifies that hidden routes are not accessible in navigation and that stable
 * routes are properly exposed. This ensures route stability semantics are
 * correctly enforced.
 *
 * @doc.type script
 * @doc.purpose Route visibility enforcement check
 * @doc.layer infrastructure
 */

import { readFileSync } from 'fs';
import { resolve } from 'path';

const ROUTE_CONTRACT_PATH = process.env.PHR_ROUTE_CONTRACT_PATH
  ? resolve(process.cwd(), process.env.PHR_ROUTE_CONTRACT_PATH)
  : resolve(process.cwd(), 'products/phr/config/phr-route-contract.json');
const ROUTE_MAP_PATH = process.env.PHR_ROUTE_MAP_PATH
  ? resolve(process.cwd(), process.env.PHR_ROUTE_MAP_PATH)
  : resolve(process.cwd(), 'products/phr/apps/web/src/routes.tsx');
const ROUTE_ELEMENTS_PATH = process.env.PHR_ROUTE_ELEMENTS_PATH
  ? resolve(process.cwd(), process.env.PHR_ROUTE_ELEMENTS_PATH)
  : resolve(process.cwd(), 'products/phr/apps/web/src/phrRouteElements.tsx');

let exitCode = 0;

function loadJson(path) {
  try {
    const content = readFileSync(path, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`❌ Failed to load ${path}:`, error.message);
    process.exit(1);
  }
}

function checkRouteVisibility() {
  console.log('🔍 Checking PHR route visibility...\n');

  const contract = loadJson(ROUTE_CONTRACT_PATH);
  const stableRoutes = contract.routes.filter(r => r.stability === 'stable').map(r => r.path);
  const hiddenRoutes = contract.routes.filter(r => r.stability === 'hidden').map(r => r.path);

  console.log(`📊 Route contract loaded:`);
  console.log(`   - Stable routes: ${stableRoutes.length}`);
  console.log(`   - Hidden routes: ${hiddenRoutes.length}\n`);

  // The router intentionally projects every canonical contract route through
  // ProtectedPhrRoute so direct links can be denied consistently. Keep this
  // check aligned to that contract-driven pattern rather than searching for
  // static path literals in the route array.
  let routeMapContent;
  try {
    routeMapContent = readFileSync(ROUTE_MAP_PATH, 'utf-8');
  } catch (error) {
    console.error(`❌ Could not read route map at ${ROUTE_MAP_PATH}: ${error.message}`);
    process.exit(1);
  }

  const requiredRouterTokens = [
    'phrRouteContracts.map(attachPhrRouteElement)',
    '...phrRouteManifest.map(protectedRoute)',
    "route.stability === 'hidden'",
    '<Navigate to="/not-found" replace />',
    "route.stability === 'blocked'",
    '<Navigate to="/forbidden" replace />',
    'isRouteAllowedForRole(route, role)',
  ];

  const missingRouterTokens = requiredRouterTokens.filter(token => !routeMapContent.includes(token));
  if (missingRouterTokens.length > 0) {
    console.error('❌ Route map is missing canonical visibility enforcement tokens:');
    missingRouterTokens.forEach(token => console.error(`   - ${token}`));
    exitCode = 1;
  } else {
    console.log('✅ Route map projects canonical contract routes through visibility guards');
  }

  // Route elements may map hidden direct links to NotFoundPage, but must never
  // make them appear as product navigation destinations.
  let routeElementsContent;
  try {
    routeElementsContent = readFileSync(
      ROUTE_ELEMENTS_PATH,
      'utf-8',
    );
  } catch (error) {
    console.error(`❌ Could not read route elements: ${error.message}`);
    process.exit(1);
  }

  const hiddenMappedToNotFound = hiddenRoutes.filter(path => {
    return !routeElementsContent.includes(`'${path}': <NotFoundPage />`);
  });

  if (hiddenMappedToNotFound.length > 0) {
    console.error('❌ Hidden routes are not mapped to NotFoundPage:');
    hiddenMappedToNotFound.forEach(path => console.error(`   - ${path}`));
    exitCode = 1;
  } else {
    console.log('✅ Hidden route direct-link elements resolve to NotFoundPage');
  }

  const stableMappedRoutes = stableRoutes.filter(path => {
    const escapedPath = path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    if (path === '/forbidden' || path === '/not-found') {
      const systemRoutePattern = new RegExp(`'${escapedPath}'\\s*:\\s*<(?:ForbiddenPage|NotFoundPage)\\s*/>`);
      return systemRoutePattern.test(routeElementsContent);
    }
    const routeElementPattern = new RegExp(`'${escapedPath}'\\s*:\\s*<(?!(?:NotFoundPage|ForbiddenPage)\\b)[A-Za-z0-9]+Page\\s*/>`);
    return routeElementPattern.test(routeElementsContent);
  });

  const stableNotMappedRoutes = stableRoutes.filter(path => !stableMappedRoutes.includes(path));
  if (stableNotMappedRoutes.length > 0) {
    console.error('❌ Stable routes missing concrete page element mappings:');
    stableNotMappedRoutes.forEach(path => console.error(`   - ${path}`));
    exitCode = 1;
  } else {
    console.log('✅ All stable routes map to concrete page elements');
  }

  const hiddenInNavigation = hiddenRoutes.filter(path => {
    const navPattern = new RegExp(`href=["']${path}["']|to=["']${path}["']`);
    return navPattern.test(routeMapContent) || navPattern.test(routeElementsContent);
  });

  if (hiddenInNavigation.length > 0) {
    console.error('❌ Hidden routes found as static navigation targets:');
    hiddenInNavigation.forEach(path => console.error(`   - ${path}`));
    exitCode = 1;
  } else {
    console.log('✅ Hidden routes are not static navigation targets');
  }

  // Check that all routes have stability field
  const routesWithoutStability = contract.routes.filter(r => !r.stability);
  if (routesWithoutStability.length > 0) {
    console.error('❌ Routes missing stability field:');
    routesWithoutStability.forEach(r => console.error(`   - ${r.path}`));
    exitCode = 1;
  } else {
    console.log('✅ All routes have stability field');
  }

  // Check that stability values are valid
  const validStabilities = ['stable', 'hidden', 'preview', 'blocked'];
  const routesWithInvalidStability = contract.routes.filter(r => !validStabilities.includes(r.stability));
  if (routesWithInvalidStability.length > 0) {
    console.error('❌ Routes with invalid stability values:');
    routesWithInvalidStability.forEach(r => console.error(`   - ${r.path}: ${r.stability}`));
    exitCode = 1;
  } else {
    console.log('✅ All routes have valid stability values');
  }

  console.log('\n' + '='.repeat(60));
  if (exitCode === 0) {
    console.log('✅ Route visibility check passed');
  } else {
    console.log('❌ Route visibility check failed');
  }
  console.log('='.repeat(60));

  process.exit(exitCode);
}

checkRouteVisibility();
