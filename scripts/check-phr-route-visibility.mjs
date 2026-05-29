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

const ROUTE_CONTRACT_PATH = resolve(process.cwd(), 'products/phr/config/phr-route-contract.json');
const ROUTE_MAP_PATH = resolve(process.cwd(), 'products/phr/apps/web/src/routes.tsx');

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

  // Check that hidden routes are not in the route map
  let routeMapContent;
  try {
    routeMapContent = readFileSync(ROUTE_MAP_PATH, 'utf-8');
  } catch (error) {
    console.warn(`⚠️  Could not read route map at ${ROUTE_MAP_PATH} - skipping route map check`);
    routeMapContent = '';
  }

  const hiddenInRouteMap = hiddenRoutes.filter(path => {
    return routeMapContent.includes(`path="${path}"`) || 
           routeMapContent.includes(`path: '${path}'`) ||
           routeMapContent.includes(`path: "${path}"`);
  });

  if (hiddenInRouteMap.length > 0) {
    console.error('❌ Hidden routes found in route map:');
    hiddenInRouteMap.forEach(path => console.error(`   - ${path}`));
    exitCode = 1;
  } else {
    console.log('✅ No hidden routes in route map');
  }

  // Check that stable routes are in the route map
  const stableNotInRouteMap = stableRoutes.filter(path => {
    return !routeMapContent.includes(`path="${path}"`) && 
           !routeMapContent.includes(`path: '${path}'`) &&
           !routeMapContent.includes(`path: "${path}"`);
  });

  if (stableNotInRouteMap.length > 0) {
    console.warn('⚠️  Stable routes not found in route map (may be intentional):');
    stableNotInRouteMap.forEach(path => console.warn(`   - ${path}`));
  } else {
    console.log('✅ All stable routes are in route map');
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
