#!/usr/bin/env tsx
/**
 * Route Validation Script
 *
 * Validates that all route files referenced in the active React Router v7
 * flat-route config (src/routes.ts) exist on disk.
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
    totalRoutes: number;
    validRoutes: number;
    missingRoutes: number;
    stubRoutes: number;
  };
}

const ROUTES_FILE = path.join(__dirname, '../web/src/routes.ts');

// Stub page size threshold (pages smaller than this are likely stubs)
const STUB_SIZE_THRESHOLD = 1000; // 1KB

function extractRoutePaths(routesContent: string): Set<string> {
  const paths = new Set<string>();

  // Match: route('path', 'routes/...') or index('routes/...') or layout('routes/...', [...])
  const routeRegex = /(?:route|index|layout)\(['"][^'"]*['"]\s*,\s*['"](routes\/[^'"]+)['"]/g;

  let match;
  while ((match = routeRegex.exec(routesContent)) !== null) {
    const [, routePath] = match;
    paths.add(routePath);
  }

  return paths;
}

function resolveRoutePath(routePath: string): string {
  const ext = routePath.endsWith('.tsx') ? '' : '.tsx';
  return path.join(path.dirname(ROUTES_FILE), `${routePath}${ext}`);
}

function isStubRoute(filePath: string): boolean {
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
      totalRoutes: 0,
      validRoutes: 0,
      missingRoutes: 0,
      stubRoutes: 0,
    },
  };

  if (!fs.existsSync(ROUTES_FILE)) {
    result.valid = false;
    result.errors.push(`Routes config not found: ${ROUTES_FILE}`);
    return result;
  }

  const routesContent = fs.readFileSync(ROUTES_FILE, 'utf-8');
  const routePaths = extractRoutePaths(routesContent);

  result.stats.totalRoutes = routePaths.size;

  console.log(`\n🔍 Validating ${routePaths.size} route files...\n`);

  for (const routePath of routePaths) {
    const filePath = resolveRoutePath(routePath);

    if (!fs.existsSync(filePath)) {
      result.valid = false;
      result.errors.push(
        `❌ Route file not found\n   Config: ${routePath}\n   Expected: ${filePath}`
      );
      result.stats.missingRoutes++;
    } else {
      result.stats.validRoutes++;

      if (isStubRoute(filePath)) {
        result.warnings.push(
          `⚠️  Stub route detected (${fs.statSync(filePath).size} bytes)\n   Path: ${filePath}`
        );
        result.stats.stubRoutes++;
      }
    }
  }

  return result;
}

function printResults(result: ValidationResult): void {
  console.log('\n' + '='.repeat(80));
  console.log('📊 VALIDATION RESULTS');
  console.log('='.repeat(80) + '\n');

  console.log('Statistics:');
  console.log(`  Total routes:   ${result.stats.totalRoutes}`);
  console.log(`  Valid routes:   ${result.stats.validRoutes} ✅`);
  console.log(`  Missing routes: ${result.stats.missingRoutes} ❌`);
  console.log(`  Stub routes:    ${result.stats.stubRoutes} ⚠️\n`);

  if (result.errors.length > 0) {
    console.log('🚨 ERRORS (Must Fix):\n');
    result.errors.forEach((error) => console.log(error + '\n'));
  }

  if (result.warnings.length > 0) {
    console.log('⚠️  WARNINGS (Consider Enhancing):\n');
    result.warnings.forEach((warning) => console.log(warning + '\n'));
  }

  console.log('='.repeat(80));
  if (result.valid) {
    console.log('✅ VALIDATION PASSED - All route files are present!');
  } else {
    console.log('❌ VALIDATION FAILED - Fix errors above before deploying!');
  }
  console.log('='.repeat(80) + '\n');
}

// Main execution
try {
  const result = validateRoutes();
  printResults(result);
  process.exit(result.valid ? 0 : 1);
} catch (error) {
  console.error('❌ Validation script failed:', error);
  process.exit(1);
}
