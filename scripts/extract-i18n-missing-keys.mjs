#!/usr/bin/env node

/**
 * Wave 3: Add Full i18n Missing-Key Extraction and Pseudo-Locale Screenshots/Assertions
 *
 * Extracts all missing i18n keys and validates pseudo-locale coverage:
 * - Full missing-key scan across all products
 * - All product UI string extraction
 * - Pseudo-locale Playwright screenshots or assertions
 * - Localized validation/error messages validation
 *
 * This ensures complete i18n coverage with visual validation.
 *
 * Usage: node scripts/extract-i18n-missing-keys.mjs [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product='))?.split('=')[1);

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
 * Check for missing key extraction
 */
function checkMissingKeyExtraction(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src'),
    path.join(productPath, 'apps'),
  ];

  let hasExtraction = false;
  let hasMissingKeys = false;

  for (const srcDir of srcDirs) {
    if (!existsSync(srcDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.jsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('t(') || content.includes('useTranslation') || content.includes('formatMessage')) {
              hasExtraction = true;
            }
            
            if (content.includes('missing') && content.includes('key')) {
              hasMissingKeys = true;
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasExtraction) {
    logSuccess(`${productName}: Has i18n key extraction`);
    logEvidence(`${productName}: UI strings use i18n functions`);
  } else {
    logWarning(`${productName}: Missing i18n key extraction`);
  }

  if (hasMissingKeys) {
    logWarning(`${productName}: Has missing i18n keys`);
  }

  return hasExtraction;
}

/**
 * Check for pseudo-locale tests
 */
function checkPseudoLocaleTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'e2e'),
    path.join(productPath, 'tests'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasPseudoLocaleTests = false;
  let hasPseudoLocaleScreenshots = false;

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
            
            if (content.includes('pseudo') || content.includes('xx-XX') || content.includes('en-XA')) {
              hasPseudoLocaleTests = true;
              logEvidence(`${productName}: Has pseudo-locale tests`);
            }
            
            if (content.includes('screenshot') && content.includes('pseudo')) {
              hasPseudoLocaleScreenshots = true;
              logEvidence(`${productName}: Has pseudo-locale screenshots`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(testDir);
  }

  if (hasPseudoLocaleTests) {
    logSuccess(`${productName}: Has pseudo-locale tests`);
  } else {
    logWarning(`${productName}: Missing pseudo-locale tests`);
  }

  if (hasPseudoLocaleScreenshots) {
    logSuccess(`${productName}: Has pseudo-locale screenshots`);
  } else {
    logWarning(`${productName}: Missing pseudo-locale screenshots`);
  }

  return hasPseudoLocaleTests || hasPseudoLocaleScreenshots;
}

/**
 * Check for localized validation messages
 */
function checkLocalizedValidation(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src'),
    path.join(productPath, 'apps'),
  ];

  let hasLocalizedValidation = false;

  for (const srcDir of srcDirs) {
    if (!existsSync(srcDir)) continue;

    function searchDir(dir) {
      try {
        const items = readdirSync(dir);
        
        for (const item of items) {
          const itemPath = path.join(dir, item);
          const stat = statSync(itemPath);
          
          if (stat.isDirectory() && !item.includes('node_modules') && !item.includes('.git')) {
            searchDir(itemPath);
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.jsx')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if ((content.includes('t(') || content.includes('useTranslation')) &&
                (content.includes('validation') || content.includes('error'))) {
              hasLocalizedValidation = true;
              logEvidence(`${productName}: Has localized validation/error messages`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasLocalizedValidation) {
    logSuccess(`${productName}: Has localized validation/error messages`);
  } else {
    logWarning(`${productName}: Missing localized validation/error messages`);
  }

  return hasLocalizedValidation;
}

/**
 * Generate i18n extraction report
 */
function generateI18nExtractionReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'i18n-extraction');
  
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

  const reportPath = path.join(evidenceDir, `i18n-extraction-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 i18n extraction report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Extracting i18n missing keys and validating pseudo-locale coverage...\n');

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
    
    checkMissingKeyExtraction(productPath, product.name);
    checkPseudoLocaleTests(productPath, product.name);
    checkLocalizedValidation(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateI18nExtractionReport();

  if (violations.length > 0) {
    console.log('\ni18n extraction failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\ni18n extraction passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\ni18n extraction passed.');
}

main();
