#!/usr/bin/env node

/**
 * PHR Feature Flag Enforcement Gate
 *
 * Validates that feature-flagged routes are not discoverable in production.
 * This script checks that:
 * 1. Routes marked with featureFlag: true in phrRouteContracts.ts are hidden in production
 * 2. The feature-visibility.json configuration aligns with route contracts
 * 3. Production builds do not expose experimental routes
 *
 * Exit code 0 = no violations.
 * Exit code 1 = one or more violations found.
 *
 * Usage:
 *   node scripts/check-phr-feature-flag-enforcement.mjs
 */

import { readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');

const ROUTE_CONTRACTS_FILE = join(REPO_ROOT, 'products/phr/apps/web/src/phrRouteContracts.ts');
const FEATURE_VISIBILITY_FILE = join(REPO_ROOT, 'products/phr/config/feature-visibility.json');

// Extract route contracts from TypeScript file
function extractRouteContracts(content) {
  const contracts = [];
  const routePattern = /{\s*path:\s*['"]([^'"]+)['"][^}]*featureFlag:\s*true[^}]*}/g;
  let match;
  while ((match = routePattern.exec(content)) !== null) {
    contracts.push(match[1]);
  }
  return contracts;
}

// Extract all routes from contracts
function extractAllRoutes(content) {
  const routes = [];
  const routePattern = /path:\s*['"]([^'"]+)['"]/g;
  let match;
  while ((match = routePattern.exec(content)) !== null) {
    routes.push(match[1]);
  }
  return routes;
}

function main() {
  console.log('🔒 Checking PHR feature flag enforcement...\n');

  let violations = [];

  // Read route contracts
  let routeContractsContent;
  try {
    routeContractsContent = readFileSync(ROUTE_CONTRACTS_FILE, 'utf-8');
  } catch (err) {
    console.error(`❌ Cannot read route contracts: ${ROUTE_CONTRACTS_FILE}`);
    process.exit(1);
  }

  // Read feature visibility config
  let featureVisibility;
  try {
    featureVisibility = JSON.parse(readFileSync(FEATURE_VISIBILITY_FILE, 'utf-8'));
  } catch (err) {
    console.error(`❌ Cannot read feature visibility config: ${FEATURE_VISIBILITY_FILE}`);
    process.exit(1);
  }

  const featureFlaggedRoutes = extractRouteContracts(routeContractsContent);
  const allRoutes = extractAllRoutes(routeContractsContent);

  // Check that feature-flagged routes are hidden in production
  console.log(`📊 Found ${featureFlaggedRoutes.length} feature-flagged routes\n`);

  for (const route of featureFlaggedRoutes) {
    // Find the feature entry that contains this route
    let featureEntry = null;
    for (const [featureId, feature] of Object.entries(featureVisibility.features)) {
      if (feature.routes && feature.routes.includes(route)) {
        featureEntry = feature;
        break;
      }
    }
    
    if (!featureEntry) {
      violations.push({
        route,
        message: 'Feature-flagged route missing from feature-visibility.json',
      });
      continue;
    }

    if (featureEntry.visibility.production !== 'hidden') {
      violations.push({
        route,
        message: `Feature-flagged route is visible in production (visibility: ${featureEntry.visibility.production})`,
      });
    }
  }

  // Check that experimental routes have featureFlag: true
  for (const [featureId, feature] of Object.entries(featureVisibility.features)) {
    if (feature.lifecycle?.stability === 'experimental' && !feature.featureFlag) {
      const routePath = feature.routes?.[0];
      if (routePath && allRoutes.includes(routePath)) {
        violations.push({
          route: routePath,
          message: 'Route has experimental stability but no featureFlag in contracts',
        });
      }
    }
  }

  // Report violations
  if (violations.length > 0) {
    console.error('❌ Feature flag enforcement violations detected:\n');
    violations.forEach((v, i) => {
      console.error(`  ${i + 1}. ${v.route}`);
      console.error(`     ${v.message}\n`);
    });
    console.error(`\n${violations.length} violation(s) found. Fix before merging to main.\n`);
    process.exit(1);
  }

  console.log('✅ Feature flag enforcement check passed.');
  console.log('✅ All feature-flagged routes are hidden in production.\n');
  process.exit(0);
}

main();
