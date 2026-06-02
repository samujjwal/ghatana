#!/usr/bin/env node

/**
 * Route DTO Schema Link Check (API-03)
 *
 * Ensures every stable API route has a DTO schema linked to route contract dtoSchemaId.
 * This validates that stable routes have proper API contract definitions.
 *
 * Usage: node scripts/check-route-dto-schema-links.mjs
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');

let violations = [];
let filesChecked = 0;

function checkRouteContract(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const relativePath = relative(REPO_ROOT, filePath);
  
  filesChecked++;
  
  try {
    const contract = JSON.parse(content);
    
    // Check if this is a route contract
    if (!contract.routes || !Array.isArray(contract.routes)) {
      return;
    }
    
    contract.routes.forEach((route, index) => {
      const routePath = route.path || `route[${index}]`;
      
      // Only check stable routes
      if (route.stability !== 'stable') {
        return;
      }
      
      // Check if route has apiEndpoint (indicates it's an API route)
      if (!route.apiEndpoint) {
        return;
      }
      
      // Check if route has dtoSchemaId
      if (!route.dtoSchemaId) {
        violations.push({
          file: relativePath,
          route: routePath,
          message: 'Stable API route missing dtoSchemaId',
          details: 'Stable API routes must have dtoSchemaId linked to DTO schema definition'
        });
      }
    });
  } catch (error) {
    // Skip files that aren't valid JSON
  }
}

function walkDirectory(dir) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip node_modules and .git
      if (entry.name !== 'node_modules' && entry.name !== '.git' && entry.name !== 'build') {
        walkDirectory(fullPath);
      }
    } else if (entry.isFile() && entry.name.endsWith('.json')) {
      // Check for route contract files
      if (entry.name.includes('route-contract') || entry.name.includes('route-contracts')) {
        checkRouteContract(fullPath);
      }
    }
  }
}

function main() {
  console.log('🔍 Checking route DTO schema links (API-03)...\n');
  
  // Check products for route contracts
  const productsDir = join(REPO_ROOT, 'products');
  if (statSync(productsDir).isDirectory()) {
    walkDirectory(productsDir);
  }
  
  console.log(`📊 Checked ${filesChecked} route contract files\n`);
  
  if (violations.length > 0) {
    console.error(`❌ Found ${violations.length} DTO schema link violations:\n`);
    violations.forEach((v, i) => {
      console.error(`  ${i + 1}. ${v.file}`);
      console.error(`     Route: ${v.route}`);
      console.error(`     ${v.message}`);
      console.error(`     ${v.details}\n`);
    });
    console.error('\n💡 Fix: Add dtoSchemaId to stable API routes in the route contract.');
    console.error('   Example: "dtoSchemaId": "phr.consent.grant.request"\n');
    process.exit(1);
  }
  
  console.log('✅ No DTO schema link violations found.');
  console.log('✅ All stable API routes have dtoSchemaId linked.\n');
  process.exit(0);
}

main();
