#!/usr/bin/env tsx
/**
 * Route Validation Script
 * 
 * Validates that all lazy-loaded page imports in routes.tsx actually exist
 * and that all existing pages are properly routed.
 * 
 * Usage: tsx scripts/validate-routes.ts
 */

import * as fs from 'fs';
import * as path from 'path';
import { glob } from 'glob';

interface ValidationResult {
  missingPages: Array<{ import: string; expectedPath: string }>;
  unroutedPages: string[];
  duplicateImports: Array<{ page: string; imports: string[] }>;
  success: boolean;
}

const ROUTES_FILE = path.join(__dirname, '../frontend/apps/web/src/router/routes.tsx');
const PAGES_DIR = path.join(__dirname, '../frontend/apps/web/src/pages');

async function validateRoutes(): Promise<ValidationResult> {
  const result: ValidationResult = {
    missingPages: [],
    unroutedPages: [],
    duplicateImports: [],
    success: true,
  };

  // Read routes file
  const routesContent = fs.readFileSync(ROUTES_FILE, 'utf-8');

  // Extract all lazy imports
  const lazyImportRegex = /const\s+(\w+)\s+=\s+lazy\(\s*\(\)\s*=>\s*import\(['"](.+?)['"]\)/g;
  const imports = new Map<string, string[]>();
  let match;

  while ((match = lazyImportRegex.exec(routesContent)) !== null) {
    const componentName = match[1];
    const importPath = match[2];
    
    if (!imports.has(importPath)) {
      imports.set(importPath, []);
    }
    imports.get(importPath)!.push(componentName);
  }

  // Check for duplicate imports
  for (const [importPath, components] of imports.entries()) {
    if (components.length > 1) {
      result.duplicateImports.push({
        page: importPath,
        imports: components,
      });
    }
  }

  // Validate each import exists
  for (const [importPath, components] of imports.entries()) {
    const fullPath = path.join(
      __dirname,
      '../frontend/apps/web/src',
      importPath + '.tsx'
    );

    if (!fs.existsSync(fullPath)) {
      result.missingPages.push({
        import: components[0],
        expectedPath: fullPath,
      });
      result.success = false;
    }
  }

  // Find all existing pages
  const allPages = await glob('**/*Page.tsx', {
    cwd: PAGES_DIR,
    absolute: false,
  });

  // Check which pages are not imported
  const importedPaths = new Set(
    Array.from(imports.keys()).map((p) => {
      const normalized = p.replace('../pages/', '');
      return normalized + '.tsx';
    })
  );

  for (const page of allPages) {
    if (!importedPaths.has(page)) {
      result.unroutedPages.push(page);
    }
  }

  return result;
}

async function main() {
  console.log('🔍 Validating YAPPC Routes...\n');

  const result = await validateRoutes();

  // Report missing pages
  if (result.missingPages.length > 0) {
    console.log('❌ MISSING PAGES (imported but don\'t exist):');
    result.missingPages.forEach(({ import: imp, expectedPath }) => {
      console.log(`   - ${imp}: ${expectedPath}`);
    });
    console.log();
  }

  // Report unrouted pages
  if (result.unroutedPages.length > 0) {
    console.log('⚠️  UNROUTED PAGES (exist but not imported):');
    result.unroutedPages.forEach((page) => {
      console.log(`   - ${page}`);
    });
    console.log();
  }

  // Report duplicate imports
  if (result.duplicateImports.length > 0) {
    console.log('⚠️  DUPLICATE IMPORTS:');
    result.duplicateImports.forEach(({ page, imports: imps }) => {
      console.log(`   - ${page}: ${imps.join(', ')}`);
    });
    console.log();
  }

  // Summary
  if (result.success && result.unroutedPages.length === 0) {
    console.log('✅ All routes are valid!\n');
    process.exit(0);
  } else {
    console.log('❌ Route validation failed. Please fix the issues above.\n');
    process.exit(1);
  }
}

main().catch((error) => {
  console.error('Error validating routes:', error);
  process.exit(1);
});
