#!/usr/bin/env node

/**
 * P2-06: Production bundle guard for frontend.
 * 
 * This script checks that the production build does not contain
 * mock/fixture/deprecatedRoutes/fake/demo/placeholder/api-mocks files
 * except in allowed test-only folders.
 *
 * @doc.type script
 * @doc.purpose Production bundle guard for frontend
 * @doc.layer frontend
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join } from 'path';

const FORBIDDEN_PATTERNS = [
  'mock',
  'fixture',
  'deprecatedRoutes',
  'fake',
  'demo',
  'placeholder',
  'api-mocks',
];

const ALLOWED_TEST_FOLDERS = [
  '__tests__',
  'test',
  'tests',
  'e2e',
  'test-fixtures',
];

function checkFile(filePath) {
  const content = readFileSync(filePath, 'utf8');
  const fileName = filePath.split('/').pop();
  
  for (const pattern of FORBIDDEN_PATTERNS) {
    if (fileName.toLowerCase().includes(pattern.toLowerCase())) {
      return {
        forbidden: true,
        pattern,
        file: filePath,
      };
    }
  }
  
  return { forbidden: false };
}

function isAllowedTestFolder(filePath) {
  const parts = filePath.split('/');
  return parts.some(part => ALLOWED_TEST_FOLDERS.includes(part));
}

function scanDirectory(dir, results = []) {
  const files = readdirSync(dir);
  
  for (const file of files) {
    const filePath = join(dir, file);
    const stat = statSync(filePath);
    
    if (stat.isDirectory()) {
      scanDirectory(filePath, results);
    } else if (file.endsWith('.js') || file.endsWith('.ts') || file.endsWith('.tsx') || file.endsWith('.jsx')) {
      if (!isAllowedTestFolder(filePath)) {
        const check = checkFile(filePath);
        if (check.forbidden) {
          results.push(check);
        }
      }
    }
  }
  
  return results;
}

function main() {
  const buildDir = process.argv[2] || 'dist';
  
  console.log(`Scanning production bundle: ${buildDir}`);
  
  if (!statSync(buildDir).exists()) {
    console.error(`Build directory not found: ${buildDir}`);
    process.exit(1);
  }
  
  const violations = scanDirectory(buildDir);
  
  if (violations.length > 0) {
    console.error('\n❌ Production bundle contains forbidden files:\n');
    for (const violation of violations) {
      console.error(`  - ${violation.file} (contains pattern: ${violation.pattern})`);
    }
    console.error('\nForbidden patterns:', FORBIDDEN_PATTERNS.join(', '));
    console.error('Allowed test folders:', ALLOWED_TEST_FOLDERS.join(', '));
    process.exit(1);
  }
  
  console.log('✅ Production bundle check passed');
}

main();
