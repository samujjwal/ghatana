#!/usr/bin/env node

/**
 * Route Service Contract Test Check (API-06)
 *
 * Ensures every stable route has a corresponding service contract test.
 * Service contract tests validate that routes follow the standard handler pattern
 * and properly delegate to application services.
 *
 * Usage: node scripts/check-route-service-contract-tests.mjs
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');

let violations = [];
let routeContractsChecked = 0;
let testFilesChecked = 0;

function extractRouteNameFromPath(routePath) {
  // Extract route name from path like "PhrConsentRoutes.java" -> "PhrConsentRoutes"
  const fileName = routePath.split('/').pop();
  return fileName.replace('.java', '');
}

function findTestFileForRoute(routeName, testDir) {
  const expectedTestName = `${routeName}ServiceContractTest.java`;
  const expectedTestPath = join(testDir, expectedTestName);
  
  try {
    if (statSync(expectedTestPath).isFile()) {
      return expectedTestPath;
    }
  } catch (e) {
    // File doesn't exist
  }
  
  return null;
}

function checkRouteContract(filePath, testDir) {
  const content = readFileSync(filePath, 'utf-8');
  const relativePath = relative(REPO_ROOT, filePath);
  
  try {
    const contract = JSON.parse(content);
    
    // Check if this is a route contract
    if (!contract.routes || !Array.isArray(contract.routes)) {
      return;
    }
    
    contract.routes.forEach((route) => {
      // Only check stable routes with API endpoints
      if (route.stability !== 'stable' || !route.apiEndpoint) {
        return;
      }
      
      routeContractsChecked++;
      
      // Try to find corresponding route Java file
      const routeName = extractRouteNameFromPath(relativePath);
      const testFile = findTestFileForRoute(routeName, testDir);
      
      if (!testFile) {
        violations.push({
          routeContract: relativePath,
          route: route.path,
          message: 'Stable API route missing service contract test',
          details: `Expected test file: ${routeName}ServiceContractTest.java`
        });
      } else {
        testFilesChecked++;
      }
    });
  } catch (error) {
    // Skip files that aren't valid JSON
  }
}

function walkDirectory(dir, callback) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip node_modules, .git, build
      if (entry.name !== 'node_modules' && entry.name !== '.git' && entry.name !== 'build') {
        walkDirectory(fullPath, callback);
      }
    } else if (entry.isFile()) {
      callback(fullPath);
    }
  }
}

function main() {
  console.log('🔍 Checking route service contract tests (API-06)...\n');
  
  const productsDir = join(REPO_ROOT, 'products');
  if (!statSync(productsDir).isDirectory()) {
    console.error('❌ Products directory not found');
    process.exit(1);
  }
  
  // Find test directory
  const testDir = join(productsDir, 'phr/src/test/java/com/ghatana/phr/api/routes');
  
  // Walk products directory for route contracts
  walkDirectory(productsDir, (filePath) => {
    if (filePath.includes('route-contract') && filePath.endsWith('.json')) {
      checkRouteContract(filePath, testDir);
    }
  });
  
  console.log(`📊 Checked ${routeContractsChecked} stable API routes\n`);
  console.log(`📊 Found ${testFilesChecked} service contract tests\n`);
  
  if (violations.length > 0) {
    console.error(`❌ Found ${violations.length} missing service contract tests:\n`);
    violations.forEach((v, i) => {
      console.error(`  ${i + 1}. ${v.routeContract}`);
      console.error(`     Route: ${v.route}`);
      console.error(`     ${v.message}`);
      console.error(`     ${v.details}\n`);
    });
    console.error('\n💡 Fix: Create service contract test using the template:');
    console.error('   docs/architecture/ROUTE_SERVICE_CONTRACT_TEST_TEMPLATE.md\n');
    process.exit(1);
  }
  
  console.log('✅ No missing service contract tests found.');
  console.log('✅ All stable API routes have service contract tests.\n');
  process.exit(0);
}

main();
