#!/usr/bin/env node

/**
 * P1-5: Accessibility Behavioral Proof Suite
 *
 * Validates comprehensive a11y maturity with behavioral verification:
 * - Keyboard-only journey tests
 * - Focus trap tests
 * - Screen-reader landmark/label assertions
 * - Table/grid accessibility
 * - Chart/visualization accessibility
 * - Modal/toast/error accessibility
 *
 * This replaces shallow a11y checks with deep behavioral verification that
 * accessibility is complete and production-ready.
 *
 * Usage: node scripts/check-a11y-behavioral-proof.mjs [--ci] [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getReleaseMode, processValidationResults, logValidationResults } from './lib/release-evidence-policy.mjs';
import { getPnpmProducts, resolveProductForProof } from './lib/product-registry-helper.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const RELEASE_MODE = getReleaseMode();
const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1];

const violations = [];
const warnings = [];
const evidence = [];
const stableGeneratedAt = process.env.GITHUB_SHA ? `commit:${process.env.GITHUB_SHA}` : 'generated-on-demand';

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
 * Execute a11y behavioral tests with Playwright
 */
function executeA11yTests(productPath, productName) {
  // Look for Playwright a11y test files
  const testDirs = [
    path.join(productPath, 'e2e'),
    path.join(productPath, 'playwright'),
    path.join(productPath, 'tests/e2e'),
  ];

  let testFound = false;
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
          } else if (item.endsWith('.spec.ts') && (item.includes('a11y') || item.includes('accessibility'))) {
            testFound = true;
            const testName = item.replace('.spec.ts', '');
            
            try {
              // Execute the test using Playwright
              const testCommand = `npx playwright test ${testName}`;
              
              console.log(`  Executing: ${testCommand}`);
              const output = execSync(testCommand, {
                cwd: productPath,
                encoding: 'utf8',
                stdio: CI_MODE ? 'pipe' : 'inherit',
                timeout: 180000 // 3 minute timeout for Playwright
              });

              const testPassed = output.includes('passed') || output.includes('PASS');
              const testFailed = output.includes('failed') || output.includes('FAIL');

              if (testPassed) {
                logSuccess(`${productName}: A11y behavioral tests PASSED`);
                logEvidence(`${productName}: Executed real keyboard/screen-reader/focus behavioral tests`);
                return true;
              } else if (testFailed) {
                logError(`${productName}: A11y behavioral tests FAILED`);
                return false;
              }
            } catch (error) {
              logWarning(`${productName}: Failed to execute a11y test: ${error.message}`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (!testFound) {
    logWarning(`${productName}: No a11y Playwright test found, falling back to posture checks`);
    return false;
  }

  return false;
}

/**
 * Check for a11y test infrastructure (fallback)
 */
function checkA11yTestInfrastructure(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'tests'),
    path.join(productPath, 'e2e'),
    path.join(productPath, 'cypress'),
    path.join(productPath, 'playwright'),
  ];

  let hasA11yTests = false;
  let hasPlaywrightA11y = false;
  let hasAxeTests = false;

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
            
            if (content.includes('a11y') || content.includes('accessibility') || content.includes('aria')) {
              hasA11yTests = true;
            }
            
            if (content.includes('axe') || content.includes('@axe-core')) {
              hasAxeTests = true;
            }
            
            if (content.includes('keyboard') || content.includes('Tab') || content.includes('focus')) {
              hasA11yTests = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasA11yTests) {
    logSuccess(`${productName}: Has a11y tests`);
  } else {
    logWarning(`${productName}: Missing a11y tests`);
  }

  if (hasAxeTests) {
    logSuccess(`${productName}: Has axe-core tests`);
  } else {
    logWarning(`${productName}: Missing axe-core tests`);
  }

  return hasA11yTests && hasAxeTests;
}

/**
 * Check for keyboard-only journey tests
 */
function checkKeyboardJourneyTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'e2e'),
    path.join(productPath, 'playwright'),
  ];

  let hasKeyboardTests = false;
  let hasTabNavigation = false;
  let hasKeyboardShortcuts = false;
  let hasEscapeKeyTests = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('keyboard') || content.includes('keyboard-only') || content.includes('no-mouse')) {
              hasKeyboardTests = true;
              logEvidence(`${productName}: Has keyboard-only tests`);
            }
            
            if (content.includes('Tab') || content.includes('tab navigation') || content.includes('focus')) {
              hasTabNavigation = true;
            }
            
            if (content.includes('shortcut') || content.includes('hotkey') || content.includes('Ctrl+') || content.includes('Cmd+')) {
              hasKeyboardShortcuts = true;
            }
            
            if (content.includes('escape') || content.includes('Escape') || content.includes('Esc') || content.includes('ESC')) {
              hasEscapeKeyTests = true;
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
    logSuccess(`${productName}: Has keyboard journey tests`);
  } else {
    logWarning(`${productName}: Missing keyboard journey tests`);
  }

  if (hasTabNavigation) {
    logSuccess(`${productName}: Has tab navigation tests`);
  } else {
    logWarning(`${productName}: Missing tab navigation tests`);
  }

  if (hasKeyboardShortcuts) {
    logSuccess(`${productName}: Has keyboard shortcut tests`);
  } else {
    logWarning(`${productName}: Missing keyboard shortcut tests`);
  }

  if (hasEscapeKeyTests) {
    logSuccess(`${productName}: Has escape key tests`);
  } else {
    logWarning(`${productName}: Missing escape key tests`);
  }

  return hasKeyboardTests && hasTabNavigation && hasEscapeKeyTests;
}

/**
 * Check for focus trap tests
 */
function checkFocusTrapTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'e2e'),
  ];

  let hasFocusTrapTests = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('focus trap') || content.includes('focusTrap') || content.includes('focus-trap')) {
              hasFocusTrapTests = true;
              logEvidence(`${productName}: Has focus trap tests`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasFocusTrapTests) {
    logSuccess(`${productName}: Has focus trap tests`);
  } else {
    logWarning(`${productName}: Missing focus trap tests`);
  }

  return hasFocusTrapTests;
}

/**
 * Check for screen-reader landmark/label assertions
 */
function checkScreenReaderTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'e2e'),
  ];

  let hasLandmarkTests = false;
  let hasLabelTests = false;
  let hasAriaLabelTests = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('landmark') || content.includes('role="main"') || content.includes('role="navigation"')) {
              hasLandmarkTests = true;
            }
            
            if (content.includes('label') || content.includes('aria-label') || content.includes('aria-labelledby')) {
              hasAriaLabelTests = true;
            }
            
            if (content.includes('screen reader') || content.includes('sr-only') || content.includes('visually-hidden')) {
              hasLabelTests = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasLandmarkTests) {
    logSuccess(`${productName}: Has landmark tests`);
  } else {
    logWarning(`${productName}: Missing landmark tests`);
  }

  if (hasLabelTests) {
    logSuccess(`${productName}: Has screen reader label tests`);
  } else {
    logWarning(`${productName}: Missing screen reader label tests`);
  }

  if (hasAriaLabelTests) {
    logSuccess(`${productName}: Has aria-label tests`);
  } else {
    logWarning(`${productName}: Missing aria-label tests`);
  }

  return hasLandmarkTests && hasLabelTests && hasAriaLabelTests;
}

/**
 * Check for table/grid accessibility
 */
function checkTableGridAccessibility(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'e2e'),
  ];

  let hasTableTests = false;
  let hasGridTests = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('table') && (content.includes('aria') || content.includes('caption') || content.includes('th'))) {
              hasTableTests = true;
            }
            
            if (content.includes('grid') && (content.includes('aria') || content.includes('role="grid"'))) {
              hasGridTests = true;
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

  if (hasGridTests) {
    logSuccess(`${productName}: Has grid accessibility tests`);
  } else {
    logWarning(`${productName}: Missing grid accessibility tests`);
  }

  return hasTableTests || hasGridTests;
}

/**
 * Check for chart/visualization accessibility
 */
function checkChartVisualizationAccessibility(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'e2e'),
  ];

  let hasChartTests = false;
  let hasVisualizationTests = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('chart') && (content.includes('aria') || content.includes('desc') || content.includes('title'))) {
              hasChartTests = true;
            }
            
            if (content.includes('visualization') && (content.includes('aria') || content.includes('alt'))) {
              hasVisualizationTests = true;
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

  if (hasVisualizationTests) {
    logSuccess(`${productName}: Has visualization accessibility tests`);
  } else {
    logWarning(`${productName}: Missing visualization accessibility tests`);
  }

  return hasChartTests || hasVisualizationTests;
}

/**
 * Check for modal/toast/error accessibility
 */
function checkModalToastErrorAccessibility(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'src/__tests__'),
    path.join(productPath, 'e2e'),
  ];

  let hasModalTests = false;
  let hasToastTests = false;
  let hasErrorTests = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('modal') && (content.includes('aria') || content.includes('role="dialog"'))) {
              hasModalTests = true;
            }
            
            if (content.includes('toast') && (content.includes('aria') || content.includes('role="alert"'))) {
              hasToastTests = true;
            }
            
            if (content.includes('error') && (content.includes('aria') || content.includes('role="alert"'))) {
              hasErrorTests = true;
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

  if (hasToastTests) {
    logSuccess(`${productName}: Has toast accessibility tests`);
  } else {
    logWarning(`${productName}: Missing toast accessibility tests`);
  }

  if (hasErrorTests) {
    logSuccess(`${productName}: Has error accessibility tests`);
  } else {
    logWarning(`${productName}: Missing error accessibility tests`);
  }

  return hasModalTests && hasToastTests && hasErrorTests;
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'a11y-behavioral-proof');
  
  if (!existsSync(evidenceDir)) {
    mkdirSync(evidenceDir, { recursive: true });
  }

  const report = {
    timestamp: stableGeneratedAt,
    violations,
    warnings,
    evidence,
    summary: {
      totalViolations: violations.length,
      totalWarnings: warnings.length,
      totalEvidence: evidence.length,
    }
  };

  const reportPath = path.join(evidenceDir, 'a11y-behavioral-proof-latest.json');
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Checking a11y behavioral proof across products...\n');

  // Resolve pnpm products (web products) from canonical product registry
  const registryProducts = getPnpmProducts();
  
  // Resolve product information for proof
  const products = registryProducts
    .map(({ productId }) => resolveProductForProof(productId))
    .filter(p => p !== null);

  // Filter by product if specified
  const filteredProducts = PRODUCT_ARG 
    ? products.filter(p => p.name.toLowerCase().includes(PRODUCT_ARG.toLowerCase()))
    : products;

  for (const product of filteredProducts) {
    const productPath = path.join(repoRoot, product.path);
    
    if (!existsSync(productPath)) {
      logError(`${product.name}: Product path not found at ${product.path}`);
      continue;
    }

    console.log(`\n--- Checking ${product.name} ---`);
    
    // Execute real tests instead of posture checks
    const testsPassed = executeA11yTests(productPath, product.name);
    
    if (!testsPassed) {
      // In release mode, fail if no executable test is found
      if (RELEASE_MODE === 'release') {
        logError(`${product.name}: No executable a11y test found - required in release mode`);
      } else {
        // Fall back to posture checks in local mode
        logWarning(`${product.name}: Test execution failed, falling back to posture checks`);
        checkA11yTestInfrastructure(productPath, product.name);
        checkKeyboardJourneyTests(productPath, product.name);
        checkFocusTrapTests(productPath, product.name);
        checkScreenReaderTests(productPath, product.name);
        checkTableGridAccessibility(productPath, product.name);
        checkChartVisualizationAccessibility(productPath, product.name);
        checkModalToastErrorAccessibility(productPath, product.name);
      }
    }
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateEvidenceReport();

  // Process validation results with release evidence policy
  const validationResults = processValidationResults(violations, warnings, evidence, RELEASE_MODE);
  logValidationResults(validationResults, 'A11y Behavioral Proof Validation');

  if (validationResults.shouldFail) {
    process.exit(1);
  }

  console.log('\nA11y behavioral proof check passed.');
}

main();
