#!/usr/bin/env node

/**
 * Route Validation Script
 * 
 * CI quality gate that validates all lazy imports in routes.tsx exist on disk.
 * Prevents broken navigation from reaching production.
 * 
 * Usage: node scripts/validate-routes.js
 * 
 * Exit codes:
 *   0 - All routes valid
 *   1 - Missing route targets found
 */

const fs = require('fs');
const path = require('path');

const ROUTES_FILE = path.join(__dirname, '../apps/web/src/router/routes.tsx');
const PAGES_DIR = path.join(__dirname, '../apps/web/src/pages');

console.log('🔍 Validating route imports...\n');

// Read routes file
let routesContent;
try {
  routesContent = fs.readFileSync(ROUTES_FILE, 'utf-8');
} catch (error) {
  console.error('❌ Could not read routes.tsx:', error.message);
  process.exit(1);
}

// Extract all lazy imports
const lazyImportRegex = /lazy\s*\(\s*\(\)\s*=>\s*import\s*\(\s*['"]([^'"]+)['"]\s*\)\s*\)/g;
const imports = [];
let match;

while ((match = lazyImportRegex.exec(routesContent)) !== null) {
  imports.push(match[1]);
}

console.log(`Found ${imports.length} lazy imports\n`);

// Validate each import
const missing = [];
const valid = [];

imports.forEach((importPath) => {
  // Convert import path to file path
  let filePath;
  
  if (importPath.startsWith('../pages/')) {
    filePath = path.join(PAGES_DIR, importPath.replace('../pages/', ''));
  } else if (importPath.startsWith('./pages/')) {
    filePath = path.join(PAGES_DIR, importPath.replace('./pages/', ''));
  } else {
    // Skip non-page imports (layouts, etc.)
    return;
  }

  // Check for .tsx extension
  const tsxPath = filePath.endsWith('.tsx') ? filePath : `${filePath}.tsx`;
  const indexPath = path.join(filePath, 'index.tsx');

  if (fs.existsSync(tsxPath)) {
    valid.push(importPath);
  } else if (fs.existsSync(indexPath)) {
    valid.push(importPath);
  } else {
    missing.push(importPath);
  }
});

// Report results
if (valid.length > 0) {
  console.log(`✅ Valid imports: ${valid.length}`);
}

if (missing.length > 0) {
  console.log(`\n❌ Missing route targets (${missing.length}):\n`);
  missing.forEach((m) => {
    console.log(`   - ${m}`);
  });
  console.log('\n🚨 Route validation FAILED');
  console.log('   Fix: Create missing page files or update routes.tsx imports\n');
  process.exit(1);
}

console.log('\n✅ All route imports are valid!');
process.exit(0);
