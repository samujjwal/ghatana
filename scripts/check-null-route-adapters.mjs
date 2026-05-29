#!/usr/bin/env node

/**
 * Check that production modules do not pass null route adapters.
 *
 * This script scans product modules for route adapter registrations
 * and ensures that null adapters are not passed in production code.
 * Null route adapters can cause runtime errors and are typically
 * a sign of incomplete or stubbed implementations.
 *
 * @doc.type script
 * @doc.purpose Validate that production modules do not pass null route adapters
 * @doc.layer governance
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const ROOT = process.cwd();
const PRODUCTS_DIR = join(ROOT, 'products');

// Patterns that indicate null route adapter usage
const NULL_ADAPTER_PATTERNS = [
  // Java patterns
  /\.with\(.*null.*\)/,
  /\.with\(.*null\s*,/,
  /RoutingServlet\.builder\([^)]*\)\.with\(null/,
  /registerRoute\(null/,
  /addRoute\(null/,
  // TypeScript/JavaScript patterns
  /routes:\s*\[\s*null/,
  /adapter:\s*null/,
  /routeAdapter:\s*null/,
  /router:\s*null/,
];

// Files to exclude (test files, mocks, fixtures)
const EXCLUDE_PATTERNS = [
  /test/,
  /spec/,
  /mock/,
  /fixture/,
  /stub/,
  /__tests__/,
  /\.test\./,
  /\.spec\./,
];

function isExcluded(filePath) {
  return EXCLUDE_PATTERNS.some(pattern => pattern.test(filePath));
}

function scanFile(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    const violations = [];

    lines.forEach((line, index) => {
      NULL_ADAPTER_PATTERNS.forEach(pattern => {
        if (pattern.test(line)) {
          violations.push({
            line: index + 1,
            pattern: pattern.source,
            content: line.trim(),
          });
        }
      });
    });

    return violations;
  } catch (error) {
    // Skip files that can't be read
    return [];
  }
}

function scanDirectory(dir, extensions = ['.java', '.ts', '.tsx', '.js', '.jsx']) {
  const violations = [];

  function scan(currentDir) {
    const entries = readdirSync(currentDir);

    for (const entry of entries) {
      const fullPath = join(currentDir, entry);
      const stat = statSync(fullPath);

      if (stat.isDirectory()) {
        // Skip node_modules and build directories
        if (!entry.includes('node_modules') && !entry.includes('build') && !entry.includes('dist')) {
          scan(fullPath);
        }
      } else if (stat.isFile()) {
        const ext = entry.substring(entry.lastIndexOf('.'));
        if (extensions.includes(ext) && !isExcluded(fullPath)) {
          const fileViolations = scanFile(fullPath);
          if (fileViolations.length > 0) {
            violations.push({ file: fullPath, violations: fileViolations });
          }
        }
      }
    }
  }

  scan(dir);
  return violations;
}

function main() {
  console.log('Checking for null route adapters in production modules...\n');

  if (!statSync(PRODUCTS_DIR).isDirectory()) {
    console.log('No products directory found, skipping check.');
    process.exit(0);
  }

  const products = readdirSync(PRODUCTS_DIR);
  let totalViolations = 0;

  for (const product of products) {
    const productPath = join(PRODUCTS_DIR, product);
    if (!statSync(productPath).isDirectory()) continue;

    console.log(`Scanning ${product}...`);
    const violations = scanDirectory(productPath);
    
    if (violations.length > 0) {
      console.log(`  ❌ Found ${violations.length} files with null route adapter usage:\n`);
      violations.forEach(({ file, violations: fileViolations }) => {
        console.log(`    ${file}`);
        fileViolations.forEach(v => {
          console.log(`      Line ${v.line}: ${v.content}`);
        });
      });
      totalViolations += violations.reduce((sum, v) => sum + v.violations.length, 0);
    } else {
      console.log(`  ✅ No null route adapters found`);
    }
  }

  console.log(`\n${totalViolations === 0 ? '✅' : '❌'} Total violations: ${totalViolations}`);

  if (totalViolations > 0) {
    console.log('\nError: Production modules must not pass null route adapters.');
    console.log('Please replace null adapters with proper implementations or feature flags.');
    process.exit(1);
  }

  process.exit(0);
}

main();
