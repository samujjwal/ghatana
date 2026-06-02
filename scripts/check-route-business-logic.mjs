#!/usr/bin/env node

/**
 * Route Business Logic Check (API-05)
 *
 * Ensures business logic lives in application/domain services, not route classes.
 * Route classes should only handle HTTP concerns (request parsing, response formatting)
 * and delegate all business logic to services.
 *
 * Usage: node scripts/check-route-business-logic.mjs
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = join(__dirname, '..');

// Patterns that indicate business logic in route files
const BUSINESS_LOGIC_PATTERNS = [
  // Database queries in routes
  /SELECT|INSERT|UPDATE|DELETE|FROM|WHERE/i,
  // Business logic method calls that should be in services
  /calculate[A-Z]|compute[A-Z]|validate[A-Z]|process[A-Z]/i,
  // Complex business logic indicators
  /if.*patient.*role|if.*consent.*granted|if.*emergency/i,
  // Direct data manipulation
  /\.map\(.*=>.*\{.*\}\).*\.filter/i,
];

// Allowed patterns in route files (HTTP concerns only)
const ALLOWED_ROUTE_PATTERNS = [
  // HTTP request/response handling
  /HttpRequest|HttpResponse|HttpStatus/i,
  // Request parsing
  /parseBody|getHeader|getPathParameter/i,
  // Response building
  /jsonResponse|errorResponse|status/i,
  // Context resolution
  /sessionContext|kernelContext|policyEvaluator/i,
  // Service delegation (this is allowed)
  /service\.|Service\(/i,
];

let violations = [];
let filesChecked = 0;

function checkRouteFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const relativePath = relative(REPO_ROOT, filePath);
  
  // Only check route files
  if (!relativePath.includes('routes') && !relativePath.includes('Routes')) {
    return;
  }
  
  filesChecked++;
  
  const lines = content.split('\n');
  lines.forEach((line, index) => {
    // Skip comments and imports
    if (line.trim().startsWith('//') || line.trim().startsWith('*') || line.trim().startsWith('import')) {
      return;
    }
    
    // Check for business logic patterns
    for (const pattern of BUSINESS_LOGIC_PATTERNS) {
      if (pattern.test(line)) {
        // Check if this line is actually delegating to a service (allowed)
        const isServiceDelegation = ALLOWED_ROUTE_PATTERNS.some(allowed => allowed.test(line));
        
        if (!isServiceDelegation) {
          violations.push({
            file: relativePath,
            line: index + 1,
            message: 'Business logic detected in route class',
            code: line.trim(),
            hint: 'Move this logic to an application/domain service'
          });
        }
      }
    }
  });
}

function walkDirectory(dir) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip node_modules, .git, build, and test directories
      if (entry.name !== 'node_modules' && entry.name !== '.git' && 
          entry.name !== 'build' && entry.name !== 'test' && 
          entry.name !== '__tests__') {
        walkDirectory(fullPath);
      }
    } else if (entry.isFile() && (entry.name.endsWith('.java') || entry.name.endsWith('.ts'))) {
      checkRouteFile(fullPath);
    }
  }
}

function main() {
  console.log('🔍 Checking route business logic separation (API-05)...\n');
  
  // Check products for route files
  const productsDir = join(REPO_ROOT, 'products');
  if (statSync(productsDir).isDirectory()) {
    walkDirectory(productsDir);
  }
  
  console.log(`📊 Checked ${filesChecked} route files\n`);
  
  if (violations.length > 0) {
    console.error(`❌ Found ${violations.length} business logic violations:\n`);
    violations.forEach((v, i) => {
      console.error(`  ${i + 1}. ${v.file}:${v.line}`);
      console.error(`     ${v.message}`);
      console.error(`     Code: ${v.code}`);
      console.error(`     Hint: ${v.hint}\n`);
    });
    console.error('\n💡 Fix: Move business logic from route classes to application/domain services.');
    console.error('   Routes should only handle HTTP concerns and delegate to services.\n');
    process.exit(1);
  }
  
  console.log('✅ No business logic violations found.');
  console.log('✅ All business logic is properly delegated to services.\n');
  process.exit(0);
}

main();
