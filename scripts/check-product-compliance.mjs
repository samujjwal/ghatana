#!/usr/bin/env node

/**
 * KER-T09: Product i18n/a11y/mobile privacy checks that can run per product
 * 
 * This script validates product compliance across three dimensions:
 * - i18n: Internationalization coverage for all routes
 * - a11y: Accessibility compliance for routes
 * - mobilePrivacy: Mobile privacy compliance for PHI handling
 * 
 * Each check can be run independently or all together.
 */

import { readFileSync, existsSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Configuration
const PRODUCTS = ['phr', 'data-cloud', 'finance', 'flashit', 'aura', 'yappc'];
const CHECK_TYPES = ['i18n', 'a11y', 'mobilePrivacy'];

function findRouteContract(product) {
  const contractPath = join(__dirname, '..', 'products', product, 'config', `${product}-route-contract.json`);
  try {
    const content = readFileSync(contractPath, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    return null;
  }
}

function findI18nFiles(product) {
  const i18nDirs = [
    join(__dirname, '..', 'products', product, 'apps', 'web', 'src', 'locales'),
    join(__dirname, '..', 'products', product, 'apps', 'mobile', 'src', 'locales'),
  ];

  const found = [];
  for (const dir of i18nDirs) {
    if (existsSync(dir)) {
      found.push(dir);
    }
  }

  return found;
}

function findPhiClassificationRegistry(product) {
  const registryPath = join(__dirname, '..', 'products', product, 'config', 'phi-classification-registry.json');
  try {
    const content = readFileSync(registryPath, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    return null;
  }
}

function checkI18nCompliance(product, routeContract) {
  console.log(`  Checking i18n compliance...`);
  
  if (!routeContract || !routeContract.routes) {
    console.log(`    ⚠️  No route contract found, skipping i18n check`);
    return { type: 'i18n', status: 'skipped', issues: [] };
  }

  const i18nDirs = findI18nFiles(product);
  const issues = [];

  // Check for routes with i18nKey
  const routesWithI18nKey = routeContract.routes.filter(r => r.i18nKey);
  const routesWithoutI18nKey = routeContract.routes.filter(r => !r.i18nKey);

  if (routesWithoutI18nKey.length > 0) {
    issues.push({
      type: 'missing_i18n_keys',
      message: `${routesWithoutI18nKey.length} routes missing i18nKey`,
      routes: routesWithoutI18nKey.map(r => r.path),
    });
  }

  // Check for i18n directories
  if (i18nDirs.length === 0) {
    issues.push({
      type: 'missing_i18n_dirs',
      message: 'No i18n locale directories found',
    });
  }

  if (issues.length === 0) {
    console.log(`    ✅ i18n compliance passed (${routesWithI18nKey.length} routes with i18n keys, ${i18nDirs.length} i18n directories)`);
  } else {
    console.log(`    ❌ i18n compliance failed with ${issues.length} issues`);
    for (const issue of issues) {
      console.log(`       - ${issue.type}: ${issue.message}`);
    }
  }

  return { type: 'i18n', status: issues.length === 0 ? 'passed' : 'failed', issues };
}

function checkA11yCompliance(product, routeContract) {
  console.log(`  Checking a11y compliance...`);
  
  if (!routeContract || !routeContract.routes) {
    console.log(`    ⚠️  No route contract found, skipping a11y check`);
    return { type: 'a11y', status: 'skipped', issues: [] };
  }

  const issues = [];

  // Check for routes with accessibility metadata
  const routesWithAccessibility = routeContract.routes.filter(r => r.accessibility);
  const routesWithoutAccessibility = routeContract.routes.filter(r => !r.accessibility);

  // For now, we don't fail if accessibility metadata is missing (it's optional)
  // but we warn about it
  if (routesWithoutAccessibility.length > 0) {
    issues.push({
      type: 'missing_accessibility',
      message: `${routesWithoutAccessibility.length} routes missing accessibility metadata (optional)`,
      routes: routesWithoutAccessibility.map(r => r.path),
      severity: 'warning',
    });
  }

  // Check for required accessibility attributes on routes that have them
  for (const route of routesWithAccessibility) {
    const requiredAttrs = ['ariaLabel', 'keyboardNav'];
    const missingAttrs = requiredAttrs.filter(attr => !route.accessibility[attr]);
    if (missingAttrs.length > 0) {
      issues.push({
        type: 'incomplete_accessibility',
        message: `Route ${route.path} missing accessibility attributes: ${missingAttrs.join(', ')}`,
        route: route.path,
        severity: 'error',
      });
    }
  }

  const errorIssues = issues.filter(i => i.severity === 'error');
  if (errorIssues.length === 0) {
    console.log(`    ✅ a11y compliance passed (${routesWithAccessibility.length} routes with accessibility metadata)`);
  } else {
    console.log(`    ❌ a11y compliance failed with ${errorIssues.length} errors`);
    for (const issue of errorIssues) {
      console.log(`       - ${issue.type}: ${issue.message}`);
    }
  }

  return { type: 'a11y', status: errorIssues.length === 0 ? 'passed' : 'failed', issues };
}

function checkMobilePrivacyCompliance(product, routeContract) {
  console.log(`  Checking mobile privacy compliance...`);
  
  if (!routeContract || !routeContract.routes) {
    console.log(`    ⚠️  No route contract found, skipping mobile privacy check`);
    return { type: 'mobilePrivacy', status: 'skipped', issues: [] };
  }

  const phiRegistry = findPhiClassificationRegistry(product);
  const issues = [];

  // Check for PHI classification registry
  if (!phiRegistry) {
    issues.push({
      type: 'missing_phi_registry',
      message: 'PHI classification registry not found',
      severity: 'warning',
    });
  }

  // Check for routes handling PHI
  const phiRoutes = routeContract.routes.filter(r => 
    r.group === 'medical' || r.group === 'documents' || r.group === 'appointments'
  );

  if (phiRoutes.length > 0 && !phiRegistry) {
    issues.push({
      type: 'phi_routes_without_registry',
      message: `${phiRoutes.length} PHI-related routes found but no PHI classification registry`,
      routes: phiRoutes.map(r => r.path),
      severity: 'error',
    });
  }

  // Check for policyId on PHI routes
  const phiRoutesWithoutPolicy = phiRoutes.filter(r => !r.policyId && !r.metadata?.policyId);
  if (phiRoutesWithoutPolicy.length > 0) {
    issues.push({
      type: 'phi_routes_without_policy',
      message: `${phiRoutesWithoutPolicy.length} PHI-related routes missing policyId`,
      routes: phiRoutesWithoutPolicy.map(r => r.path),
      severity: 'error',
    });
  }

  const errorIssues = issues.filter(i => i.severity === 'error');
  if (errorIssues.length === 0) {
    console.log(`    ✅ mobile privacy compliance passed (${phiRoutes.length} PHI-related routes)`);
  } else {
    console.log(`    ❌ mobile privacy compliance failed with ${errorIssues.length} errors`);
    for (const issue of errorIssues) {
      console.log(`       - ${issue.type}: ${issue.message}`);
    }
  }

  return { type: 'mobilePrivacy', status: errorIssues.length === 0 ? 'passed' : 'failed', issues };
}

function checkProductCompliance(product, checkTypes = CHECK_TYPES) {
  console.log(`\nChecking compliance for ${product}...`);
  
  const routeContract = findRouteContract(product);
  const results = [];

  for (const checkType of checkTypes) {
    let result;
    switch (checkType) {
      case 'i18n':
        result = checkI18nCompliance(product, routeContract);
        break;
      case 'a11y':
        result = checkA11yCompliance(product, routeContract);
        break;
      case 'mobilePrivacy':
        result = checkMobilePrivacyCompliance(product, routeContract);
        break;
      default:
        console.log(`  ⚠️  Unknown check type: ${checkType}`);
        continue;
    }
    results.push(result);
  }

  const failed = results.filter(r => r.status === 'failed');
  const passed = results.filter(r => r.status === 'passed');
  const skipped = results.filter(r => r.status === 'skipped');

  console.log(`  Summary: ${passed.length} passed, ${failed.length} failed, ${skipped.length} skipped`);

  return { product, results, status: failed.length === 0 ? 'passed' : 'failed' };
}

function main() {
  const args = process.argv.slice(2);
  const productArg = args.find(arg => !arg.startsWith('--'));
  const checkTypesArg = args.find(arg => arg.startsWith('--checks='))?.split('=')[1]?.split(',') || CHECK_TYPES;

  console.log('Product Compliance Check (KER-T09)');
  console.log('=====================================\n');

  const productsToCheck = productArg ? [productArg] : PRODUCTS;
  const results = [];

  for (const product of productsToCheck) {
    const result = checkProductCompliance(product, checkTypesArg);
    results.push(result);
  }

  console.log('\n=====================================');
  console.log('Overall Summary:');
  
  const passed = results.filter(r => r.status === 'passed');
  const failed = results.filter(r => r.status === 'failed');
  
  console.log(`  Passed: ${passed.length}`);
  console.log(`  Failed: ${failed.length}`);
  console.log(`  Total: ${results.length}`);

  if (failed.length > 0) {
    console.log('\n❌ Compliance check failed');
    process.exit(1);
  }

  console.log('\n✅ Compliance check passed');
}

main();
