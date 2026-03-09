#!/usr/bin/env tsx
/**
 * Route Validation Script
 * 
 * Validates that all lazy imports in routes.tsx exist on disk.
 * Prevents runtime navigation failures from broken imports.
 * 
 * Usage: pnpm tsx scripts/validate-routes.ts
 */

import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
  stats: {
    totalImports: number;
    validImports: number;
    missingImports: number;
    stubPages: number;
  };
}

const ROUTES_FILE = path.join(__dirname, '../apps/web/src/router/routes.tsx');
const PAGES_DIR = path.join(__dirname, '../apps/web/src/pages');

// Stub page size threshold (pages smaller than this are likely stubs)
const STUB_SIZE_THRESHOLD = 1000; // 1KB

function extractLazyImports(routesContent: string): Map<string, string> {
  const imports = new Map<string, string>();
  
  // Match: const PageName = lazy(() => import('path'));
  const lazyImportRegex = /const\s+(\w+)\s+=\s+lazy\(\s*\(\)\s*=>\s*import\(['"]([^'"]+)['"]\)/g;
  
  let match;
  while ((match = lazyImportRegex.exec(routesContent)) !== null) {
    const [, componentName, importPath] = match;
    imports.set(componentName, importPath);
  }
  
  return imports;
}

function resolveImportPath(importPath: string): string {
  // Convert relative import to absolute file path
  // '../pages/auth/LoginPage' -> '/apps/web/src/pages/auth/LoginPage.tsx'
  const relativePath = importPath.replace(/^\.\.\/pages\//, '');
  return path.join(PAGES_DIR, `${relativePath}.tsx`);
}

function isStubPage(filePath: string): boolean {
  try {
    const stats = fs.statSync(filePath);
    return stats.size < STUB_SIZE_THRESHOLD;
  } catch {
    return false;
  }
}

function validateRoutes(): ValidationResult {
  const result: ValidationResult = {
    valid: true,
    errors: [],
    warnings: [],
    stats: {
      totalImports: 0,
      validImports: 0,
      missingImports: 0,
      stubPages: 0,
    },
  };

  // Read routes file
  if (!fs.existsSync(ROUTES_FILE)) {
    result.valid = false;
    result.errors.push(`Routes file not found: ${ROUTES_FILE}`);
    return result;
  }

  const routesContent = fs.readFileSync(ROUTES_FILE, 'utf-8');
  const imports = extractLazyImports(routesContent);

  result.stats.totalImports = imports.size;

  console.log(`\n🔍 Validating ${imports.size} route imports...\n`);

  // Validate each import
  for (const [componentName, importPath] of imports) {
    const filePath = resolveImportPath(importPath);
    
    if (!fs.existsSync(filePath)) {
      result.valid = false;
      result.errors.push(
        `❌ ${componentName}: File not found\n   Import: ${importPath}\n   Expected: ${filePath}`
      );
      result.stats.missingImports++;
    } else {
      result.stats.validImports++;
      
      // Check if it's a stub page
      if (isStubPage(filePath)) {
        result.warnings.push(
          `⚠️  ${componentName}: Stub page detected (${fs.statSync(filePath).size} bytes)\n   Path: ${filePath}`
        );
        result.stats.stubPages++;
      }
    }
  }

  return result;
}

function printResults(result: ValidationResult): void {
  console.log('\n' + '='.repeat(80));
  console.log('📊 VALIDATION RESULTS');
  console.log('='.repeat(80) + '\n');

  // Stats
  console.log('Statistics:');
  console.log(`  Total imports:   ${result.stats.totalImports}`);
  console.log(`  Valid imports:   ${result.stats.validImports} ✅`);
  console.log(`  Missing imports: ${result.stats.missingImports} ❌`);
  console.log(`  Stub pages:      ${result.stats.stubPages} ⚠️\n`);

  // Errors
  if (result.errors.length > 0) {
    console.log('🚨 ERRORS (Must Fix):\n');
    result.errors.forEach((error) => console.log(error + '\n'));
  }

  // Warnings
  if (result.warnings.length > 0) {
    console.log('⚠️  WARNINGS (Consider Enhancing):\n');
    result.warnings.forEach((warning) => console.log(warning + '\n'));
  }

  // Final status
  console.log('='.repeat(80));
  if (result.valid) {
    console.log('✅ VALIDATION PASSED - All route imports are valid!');
  } else {
    console.log('❌ VALIDATION FAILED - Fix errors above before deploying!');
  }
  console.log('='.repeat(80) + '\n');
}

// Main execution
try {
  const result = validateRoutes();
  printResults(result);
  
  // Exit with error code if validation failed
  process.exit(result.valid ? 0 : 1);
} catch (error) {
  console.error('❌ Validation script failed:', error);
  process.exit(1);
}
