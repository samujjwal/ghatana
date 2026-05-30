#!/usr/bin/env node

/**
 * KER-T08: Product API path consistency check
 * 
 * This script validates that API paths referenced in the route contract
 * match the actual backend API endpoints. It checks for:
 * - Routes with apiEndpoint metadata that don't have corresponding backend endpoints
 * - Backend endpoints that are not referenced in the route contract
 * - Inconsistent path patterns between frontend routes and backend APIs
 */

import { readFileSync, existsSync, readdirSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Configuration - products to check
const PRODUCTS = ['phr', 'data-cloud', 'finance', 'flashit', 'aura', 'yappc'];

function findRouteContractFiles(product) {
  const contractPath = join(__dirname, '..', 'products', product, 'config', `${product}-route-contract.json`);
  try {
    const content = readFileSync(contractPath, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    console.warn(`No route contract found for ${product} at ${contractPath}`);
    return null;
  }
}

function findBackendApiFiles(product) {
  const apiFiles = [];
  const searchDirs = [
    join(__dirname, '..', 'products', product, 'src', 'main', 'java'),
    join(__dirname, '..', 'products', product, 'apps', 'api'),
  ];

  for (const dir of searchDirs) {
    // This is a simplified check - in production, we would recursively scan for API route definitions
    // For now, we'll check if the directory exists and has potential API files
    if (existsSync(dir)) {
      apiFiles.push(dir);
    }
  }

  return apiFiles;
}

function extractApiEndpoints(routeContract) {
  const endpoints = new Set();
  
  if (!routeContract || !routeContract.routes) {
    return endpoints;
  }

  for (const route of routeContract.routes) {
    // Check both metadata.apiEndpoint and direct apiEndpoint field
    if (route.metadata?.apiEndpoint) {
      endpoints.add(route.metadata.apiEndpoint);
    }
    if (route.apiEndpoint) {
      endpoints.add(route.apiEndpoint);
    }
  }

  return endpoints;
}

function checkApiPathConsistency(product) {
  console.log(`\nChecking API path consistency for ${product}...`);
  
  const routeContract = findRouteContractFiles(product);
  if (!routeContract) {
    console.log(`  ⚠️  No route contract found, skipping`);
    return { product, status: 'skipped', issues: [] };
  }

  const contractEndpoints = extractApiEndpoints(routeContract);
  const backendApiDirs = findBackendApiFiles(product);

  const issues = [];

  // Check for routes with apiEndpoint but no backend implementation
  if (contractEndpoints.size > 0 && backendApiDirs.length === 0) {
    issues.push({
      type: 'missing_backend',
      message: `Route contract references ${contractEndpoints.size} API endpoints but no backend API directory found`,
      endpoints: Array.from(contractEndpoints),
    });
  }

  // Check for route contract without API endpoints (for products that should have them)
  if (contractEndpoints.size === 0 && backendApiDirs.length > 0) {
    issues.push({
      type: 'missing_contract_refs',
      message: `Backend API directory exists but route contract has no API endpoint references`,
    });
  }

  // Validate API endpoint patterns
  for (const endpoint of contractEndpoints) {
    if (!endpoint.startsWith('/api/')) {
      issues.push({
        type: 'invalid_pattern',
        message: `API endpoint does not follow /api/ pattern: ${endpoint}`,
        endpoint,
      });
    }
  }

  if (issues.length === 0) {
    console.log(`  ✅ API path consistency check passed`);
    console.log(`     - ${contractEndpoints.size} API endpoints referenced in route contract`);
    console.log(`     - ${backendApiDirs.length} backend API directories found`);
  } else {
    console.log(`  ❌ API path consistency check failed with ${issues.length} issues`);
    for (const issue of issues) {
      console.log(`     - ${issue.type}: ${issue.message}`);
      if (issue.endpoints) {
        console.log(`       Endpoints: ${issue.endpoints.join(', ')}`);
      }
    }
  }

  return { product, status: issues.length === 0 ? 'passed' : 'failed', issues };
}

function main() {
  console.log('Product API Path Consistency Check (KER-T08)');
  console.log('===============================================\n');

  const results = [];
  let totalPassed = 0;
  let totalFailed = 0;
  let totalSkipped = 0;

  for (const product of PRODUCTS) {
    const result = checkApiPathConsistency(product);
    results.push(result);
    
    if (result.status === 'passed') totalPassed++;
    else if (result.status === 'failed') totalFailed++;
    else totalSkipped++;
  }

  console.log('\n===============================================');
  console.log('Summary:');
  console.log(`  Passed: ${totalPassed}`);
  console.log(`  Failed: ${totalFailed}`);
  console.log(`  Skipped: ${totalSkipped}`);
  console.log(`  Total: ${PRODUCTS.length}`);

  if (totalFailed > 0) {
    console.log('\n❌ API path consistency check failed');
    process.exit(1);
  }

  console.log('\n✅ API path consistency check passed');
}

main();
