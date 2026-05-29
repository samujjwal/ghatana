#!/usr/bin/env node

/**
 * Check that hidden routes are not visible in navigation and stable routes are.
 *
 * This script validates the route contract to ensure:
 * - Hidden routes are not discoverable in navigation
 * - Stable routes are discoverable in navigation
 * - Preview routes have appropriate visibility flags
 *
 * @doc.type script
 * @doc.purpose Validate route visibility semantics in route contracts
 * @doc.layer governance
 */

import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const ROOT = process.cwd();
const PHR_ROUTE_CONTRACT = join(ROOT, 'products', 'phr', 'config', 'phr-route-contract.json');

function loadRouteContract() {
  if (!existsSync(PHR_ROUTE_CONTRACT)) {
    console.log('PHR route contract not found, skipping check.');
    return null;
  }
  
  const content = readFileSync(PHR_ROUTE_CONTRACT, 'utf-8');
  return JSON.parse(content);
}

function validateRouteVisibility(contract) {
  const violations = [];
  const warnings = [];

  contract.routes.forEach(route => {
    const { path, stability, label } = route;

    // Hidden routes should not be discoverable
    if (stability === 'hidden') {
      if (route.discoverable !== false) {
        violations.push({
          path,
          issue: 'Hidden route is discoverable',
          severity: 'error',
          message: `Route "${path}" is marked as hidden but may be discoverable in navigation.`
        });
      }
    }

    // Stable routes should be discoverable
    if (stability === 'stable') {
      if (route.discoverable === false) {
        violations.push({
          path,
          issue: 'Stable route is not discoverable',
          severity: 'error',
          message: `Route "${path}" is marked as stable but is not discoverable in navigation.`
        });
      }
    }

    // Preview routes should have explicit discoverable flag
    if (stability === 'preview' && route.discoverable === undefined) {
      warnings.push({
        path,
        issue: 'Preview route lacks explicit discoverable flag',
        severity: 'warning',
        message: `Route "${path}" is preview but has no explicit discoverable flag.`
      });
    }

    // Blocked routes should not be discoverable
    if (stability === 'blocked') {
      if (route.discoverable !== false) {
        violations.push({
          path,
          issue: 'Blocked route is discoverable',
          severity: 'error',
          message: `Route "${path}" is marked as blocked but may be discoverable in navigation.`
        });
      }
    }
  });

  return { violations, warnings };
}

function main() {
  console.log('Checking route visibility semantics...\n');

  const contract = loadRouteContract();
  if (!contract) {
    process.exit(0);
  }

  console.log(`Loaded route contract for ${contract.product} with ${contract.routes.length} routes.\n`);

  const { violations, warnings } = validateRouteVisibility(contract);

  if (warnings.length > 0) {
    console.log(`⚠️  Warnings (${warnings.length}):\n`);
    warnings.forEach(w => {
      console.log(`  ${w.path}: ${w.message}`);
    });
    console.log();
  }

  if (violations.length > 0) {
    console.log(`❌ Errors (${violations.length}):\n`);
    violations.forEach(v => {
      console.log(`  ${v.path}: ${v.message}`);
    });
    console.log(`\nTotal violations: ${violations.length}`);
    console.log('Route visibility check failed.');
    process.exit(1);
  }

  console.log(`✅ All ${contract.routes.length} routes have correct visibility semantics.`);
  console.log('Route visibility check passed.');
  process.exit(0);
}

main();
