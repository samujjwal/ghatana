#!/usr/bin/env node

/**
 * Validate route stability - ensures routes marked as stable have all required components.
 *
 * @doc.type script
 * @doc.purpose Validate route stability against UI/API/policy/test existence
 * @doc.layer scripts
 * @doc.pattern Validation
 */

import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

/**
 * Parse JSON file
 */
function parseJson(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  return JSON.parse(content);
}

/**
 * Check if a route has all required components for stability
 */
function validateRouteStability(route, product) {
  const errors = [];
  const warnings = [];

  // Check required metadata fields
  if (!route.apiEndpoint) {
    errors.push('Missing apiEndpoint');
  }
  if (!route.policyId) {
    errors.push('Missing policyId');
  }
  if (!route.testId) {
    errors.push('Missing testId');
  }

  // Check surface-specific requirements
  if (route.surface) {
    if (route.surface.includes('web') && route.routeType === 'page') {
      // Web page routes should have UI components
      // This is a placeholder - actual check would verify component exists
      warnings.push('Web page route - verify UI component exists');
    }
    if (route.surface.includes('backend')) {
      // Backend routes should have API implementation
      // This is a placeholder - actual check would verify route exists
      warnings.push('Backend route - verify API implementation exists');
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

/**
 * Main validation function
 */
function main() {
  const productConfigs = [
    join(REPO_ROOT, 'products', 'phr', 'config', 'phr-route-contract.json'),
  ];

  let totalErrors = 0;
  let totalWarnings = 0;
  let totalStableRoutes = 0;
  let routesToDemote = [];

  console.log('Validating route stability against UI/API/policy/test requirements...\n');

  for (const configPath of productConfigs) {
    if (!existsSync(configPath)) {
      console.log(`⚠️  Skipping ${configPath} (not found)`);
      continue;
    }

    const config = parseJson(configPath);
    const routes = config.routes || [];

    for (const route of routes) {
      if (route.stability !== 'stable') {
        continue;
      }

      totalStableRoutes++;
      const result = validateRouteStability(route, 'phr');

      totalErrors += result.errors.length;
      totalWarnings += result.warnings.length;

      if (!result.valid) {
        routesToDemote.push({
          path: route.path,
          errors: result.errors,
        });
        console.log(`❌ ${route.path}: ${result.errors.join(', ')}`);
      } else if (result.warnings.length > 0) {
        console.log(`⚠️  ${route.path}: ${result.warnings.join(', ')}`);
      } else {
        console.log(`✅ ${route.path}: stable`);
      }
    }
  }

  console.log(`\nSummary: ${totalStableRoutes} stable routes checked, ${totalErrors} errors, ${totalWarnings} warnings`);

  if (routesToDemote.length > 0) {
    console.log(`\nRoutes to demote to preview:`);
    for (const route of routesToDemote) {
      console.log(`  - ${route.path}: ${route.errors.join(', ')}`);
    }
    console.log('\nTo demote these routes, update their stability field to "preview" in the route contract.');
  }

  if (totalErrors > 0) {
    process.exit(1);
  }
}

main();
