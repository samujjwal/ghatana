#!/usr/bin/env node

/**
 * Verify that stable routes have real page components.
 *
 * @doc.type script
 * @doc.purpose Verify stable routes have real page components
 * @doc.layer scripts
 * @doc.pattern Validation
 */

import { readFileSync, existsSync, readdirSync } from 'fs';
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
 * Get all page components from the pages directory
 */
function getPageComponents(pagesDir) {
  if (!existsSync(pagesDir)) {
    return [];
  }
  const files = readdirSync(pagesDir);
  return files.filter(f => f.endsWith('.tsx') && !f.endsWith('.test.tsx'));
}

/**
 * Verify stable routes have page components
 */
function verifyStableRouteComponents(routeContract, pagesDir) {
  const errors = [];
  const warnings = [];
  const pageComponents = getPageComponents(pagesDir);
  const pageComponentNames = new Set(
    pageComponents.map(f => f.replace('.tsx', ''))
  );

  for (const route of routeContract.routes) {
    if (route.stability !== 'stable') {
      continue;
    }

    if (route.routeType !== 'page') {
      continue;
    }

    if (!route.surface || !route.surface.includes('web')) {
      continue;
    }

    // Derive expected page component name from route path
    const routePath = route.path;
    let expectedComponentName = routePath
      .split('/')
      .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
      .join('');

    // Handle special naming cases
    if (expectedComponentName === 'Consents') {
      expectedComponentName = 'Consent';
    }
    if (expectedComponentName === 'EmergencyReviews') {
      expectedComponentName = 'EmergencyReviews';
    }

    // Add Page suffix
    expectedComponentName += 'Page';

    // Handle special cases
    if (routePath === '/release-readiness') {
      expectedComponentName = 'ReleaseCockpitPage';
    }

    const hasComponent = pageComponentNames.has(expectedComponentName);

    if (!hasComponent) {
      errors.push(
        `Stable route ${route.path} (${route.label}) is missing page component: ${expectedComponentName}.tsx`
      );
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
  const routeContractPath = join(REPO_ROOT, 'products', 'phr', 'config', 'phr-route-contract.json');
  const pagesDir = join(REPO_ROOT, 'products', 'phr', 'apps', 'web', 'src', 'pages');

  if (!existsSync(routeContractPath)) {
    console.log(`❌ Route contract not found: ${routeContractPath}`);
    process.exit(1);
  }

  const routeContract = parseJson(routeContractPath);

  console.log('Verifying stable routes have real page components...\n');

  const result = verifyStableRouteComponents(routeContract, pagesDir);

  if (result.errors.length > 0) {
    console.log('❌ Missing page components:');
    for (const error of result.errors) {
      console.log(`   - ${error}`);
    }
  }

  if (result.warnings.length > 0) {
    console.log('⚠️  Warnings:');
    for (const warning of result.warnings) {
      console.log(`   - ${warning}`);
    }
  }

  if (result.errors.length === 0 && result.warnings.length === 0) {
    console.log('✅ All stable routes have page components');
  }

  console.log(`\nSummary: ${result.errors.length} errors, ${result.warnings.length} warnings`);

  if (result.errors.length > 0) {
    process.exit(1);
  }
}

main();
