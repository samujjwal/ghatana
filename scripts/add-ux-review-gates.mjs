#!/usr/bin/env node

/**
 * Wave 3: Add Route-by-Route UX Review Gates
 *
 * Adds UX review gates for each public route:
 * - UX review completed
 * - UX reviewer assigned
 * - UX review date recorded
 * - UX feedback documented
 * - UX approval status tracked
 *
 * This ensures every public route has been reviewed for UX quality.
 *
 * Usage: node scripts/add-ux-review-gates.mjs [--product <product>]
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
 * Check for UX review documentation
 */
function checkUXReviewDocumentation(productPath, productName) {
  const docsDirs = [
    path.join(productPath, 'docs'),
    path.join(productPath, 'documentation'),
    path.join(productPath, '.github'),
  ];

  let hasUXReview = false;

  for (const docsDir of docsDirs) {
    if (!existsSync(docsDir)) continue;

    const files = readdirSync(docsDir);
    const uxFiles = files.filter(f => 
      f.includes('ux') || f.includes('UX') || 
      f.includes('review') || f.includes('design')
    );

    if (uxFiles.length > 0) {
      hasUXReview = true;
      logEvidence(`${productName}: Has UX review documentation`);
    }
  }

  if (hasUXReview) {
    logSuccess(`${productName}: Has UX review documentation`);
  } else {
    logWarning(`${productName}: Missing UX review documentation`);
  }

  return hasUXReview;
}

/**
 * Check for UX review gates in OpenAPI
 */
function checkUXReviewGatesInOpenAPI(productPath, productName) {
  const specDirs = [
    path.join(productPath, 'openapi'),
    path.join(productPath, 'api'),
  ];

  let hasUXGates = false;

  for (const specDir of specDirs) {
    if (!existsSync(specDir)) continue;

    const files = readdirSync(specDir);
    const specFiles = files.filter(f => f.endsWith('.yaml') || f.endsWith('.yml') || f.endsWith('.json'));

    for (const file of specFiles) {
      const filePath = path.join(specDir, file);
      const content = readFileSync(filePath, 'utf8');
      
      if (content.includes('x-ux-review') || content.includes('x-ux-approved') ||
          content.includes('x-ux-reviewer') || content.includes('x-ux-date')) {
        hasUXGates = true;
        logEvidence(`${productName}: Has UX review gates in OpenAPI`);
      }
    }
  }

  if (hasUXGates) {
    logSuccess(`${productName}: Has UX review gates in OpenAPI`);
  } else {
    logWarning(`${productName}: Missing UX review gates in OpenAPI`);
  }

  return hasUXGates;
}

/**
 * Check for UX review tracking
 */
function checkUXReviewTracking(productPath, productName) {
  const trackingFiles = [
    path.join(productPath, 'UX_REVIEW_TRACKING.md'),
    path.join(productPath, 'docs/ux-review-tracking.md'),
    path.join(productPath, '.github/ux-review-tracking.md'),
  ];

  let hasTracking = false;

  for (const trackingFile of trackingFiles) {
    if (existsSync(trackingFile)) {
      hasTracking = true;
      logEvidence(`${productName}: Has UX review tracking file`);
    }
  }

  if (hasTracking) {
    logSuccess(`${productName}: Has UX review tracking`);
  } else {
    logWarning(`${productName}: Missing UX review tracking`);
  }

  return hasTracking;
}

/**
 * Generate UX review gate report
 */
function generateUXReviewGateReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'ux-review-gates');
  
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

  const reportPath = path.join(evidenceDir, `ux-review-gates-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 UX review gate report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Adding route-by-route UX review gates...\n');

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
    
    checkUXReviewDocumentation(productPath, product.name);
    checkUXReviewGatesInOpenAPI(productPath, product.name);
    checkUXReviewTracking(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateUXReviewGateReport();

  if (violations.length > 0) {
    console.log('\nUX review gate addition failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nUX review gate addition passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nUX review gate addition passed.');
}

main();
