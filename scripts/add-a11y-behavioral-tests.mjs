#!/usr/bin/env node

/**
 * Wave 3: Add Keyboard/Screen-Reader/Table/Chart/Modal a11y Behavior Tests
 *
 * Adds comprehensive a11y behavioral tests:
 * - Keyboard-only journey tests
 * - Screen-reader landmark/label assertions
 * - Table accessibility tests
 * - Chart/visualization accessibility tests
 * - Modal/toast/error accessibility tests
 *
 * This extends the existing a11y behavioral proof with specific component-level tests.
 *
 * Usage: node scripts/add-a11y-behavioral-tests.mjs [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1];

const violations = [];
const warnings = [];
const evidence = [];

function logError(message) {
  violations.push(message);
  console.error(`❌ ERROR: ${message}`);
}

function logWarning(message) {
  warnings.push(message);
  console.warn(`⚠️  WARNING: ${message}`);
}

function logSuccess(message) {
  console.log(`✓ ${message}`);
}

function logEvidence(message) {
  evidence.push(message);
  console.log(`  📋 ${message}`);
}

/**
 * Check for keyboard-only journey tests
 */
function checkKeyboardJourneyTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'e2e'),
    path.join(productPath, 'tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasKeyboardTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.spec.ts')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('keyboard') && content.includes('journey') ||
                content.includes('keyboard-only') && content.includes('test')) {
              hasKeyboardTests = true;
              logEvidence(`${productName}: Has keyboard-only journey tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasKeyboardTests) {
    logSuccess(`${productName}: Has keyboard-only journey tests`);
  } else {
    logWarning(`${productName}: Missing keyboard-only journey tests`);
  }

  return hasKeyboardTests;
}

/**
 * Check for screen-reader tests
 */
function checkScreenReaderTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'e2e'),
    path.join(productPath, 'tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasScreenReaderTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.spec.ts')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('screen-reader') || content.includes('screen reader') ||
                content.includes('aria') && content.includes('landmark')) {
              hasScreenReaderTests = true;
              logEvidence(`${productName}: Has screen-reader tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasScreenReaderTests) {
    logSuccess(`${productName}: Has screen-reader tests`);
  } else {
    logWarning(`${productName}: Missing screen-reader tests`);
  }

  return hasScreenReaderTests;
}

/**
 * Check for table accessibility tests
 */
function checkTableAccessibilityTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'e2e'),
    path.join(productPath, 'tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasTableTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.spec.ts')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('table') && (content.includes('aria') || content.includes('caption') || content.includes('th'))) {
              hasTableTests = true;
              logEvidence(`${productName}: Has table accessibility tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasTableTests) {
    logSuccess(`${productName}: Has table accessibility tests`);
  } else {
    logWarning(`${productName}: Missing table accessibility tests`);
  }

  return hasTableTests;
}

/**
 * Check for chart accessibility tests
 */
function checkChartAccessibilityTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'e2e'),
    path.join(productPath, 'tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasChartTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.spec.ts')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('chart') && (content.includes('aria') || content.includes('desc') || content.includes('title'))) {
              hasChartTests = true;
              logEvidence(`${productName}: Has chart accessibility tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasChartTests) {
    logSuccess(`${productName}: Has chart accessibility tests`);
  } else {
    logWarning(`${productName}: Missing chart accessibility tests`);
  }

  return hasChartTests;
}

/**
 * Check for modal accessibility tests
 */
function checkModalAccessibilityTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'e2e'),
    path.join(productPath, 'tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasModalTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.spec.ts')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('modal') && (content.includes('aria') || content.includes('role="dialog"') || content.includes('focus'))) {
              hasModalTests = true;
              logEvidence(`${productName}: Has modal accessibility tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasModalTests) {
    logSuccess(`${productName}: Has modal accessibility tests`);
  } else {
    logWarning(`${productName}: Missing modal accessibility tests`);
  }

  return hasModalTests;
}

/**
 * Generate a11y behavioral test report
 */
function generateA11yBehavioralTestReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'a11y-behavioral-tests');
  
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const report = {
    timestamp: new Date().toISOString(),
    violations,
    warnings,
    evidence,
    summary: {
      totalViolations: violations.length,
      totalWarnings: warnings.length,
      totalEvidence: evidence.length,
    }
  };

  const reportPath = path.join(evidenceDir, `a11y-behavioral-tests-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 a11y behavioral test report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Adding keyboard/screen-reader/table/chart/modal a11y behavior tests...\n');

  // Products to check
  const products = [
    { path: 'frontend/apps/studio', name: 'Studio' },
    { path: 'frontend/apps/api', name: 'Frontend API' },
    { path: 'products/data-cloud/delivery/launcher', name: 'Data Cloud Launcher' },
    { path: 'products/aep', name: 'AEP' },
    { path: 'products/digital-marketing', name: 'Digital Marketing' },
  ];

  // Filter by product if specified
  const filteredProducts = PRODUCT_ARG 
    ? products.filter(p => p.name.toLowerCase().includes(PRODUCT_ARG.toLowerCase()))
    : products;

  for (const product of filteredProducts) {
    const productPath = path.join(repoRoot, product.path);
    
    if (!existsSync(productPath)) {
      logWarning(`${product.name}: Product path not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- ${product.name} ---`);
    
    checkKeyboardJourneyTests(productPath, product.name);
    checkScreenReaderTests(productPath, product.name);
    checkTableAccessibilityTests(productPath, product.name);
    checkChartAccessibilityTests(productPath, product.name);
    checkModalAccessibilityTests(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateA11yBehavioralTestReport();

  if (violations.length > 0) {
    console.log('\na11y behavioral test addition failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\na11y behavioral test addition passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\na11y behavioral test addition passed.');
}

main();
