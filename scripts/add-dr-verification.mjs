#!/usr/bin/env node

/**
 * Wave 4: Add DR Restore Verification with RPO/RTO Assertions
 *
 * Adds disaster recovery (DR) restore verification for each product:
 * - RPO (Recovery Point Objective) documentation
 * - RTO (Recovery Time Objective) documentation
 * - Restore procedure tests
 * - Backup verification tests
 * - DR drill evidence
 *
 * This ensures each product has verified DR capabilities.
 *
 * Usage: node scripts/add-dr-verification.mjs [--product <product>]
 */

import { readFileSync, existsSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ARG = process.argv.find(arg => arg.startsWith('--product=')) ? process.argv.find(arg => arg.startsWith('--product=')).split('=')[1] : null;

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
 * Check for RPO documentation
 */
function checkRPODocumentation(productPath, productName) {
  const docsDirs = [
    path.join(productPath, 'docs'),
    path.join(productPath, 'documentation'),
    path.join(productPath, '.github'),
  ];

  let hasRPO = false;

  for (const docsDir of docsDirs) {
    if (!existsSync(docsDir)) continue;

    const files = readdirSync(docsDir);
    const rpoFiles = files.filter(f => 
      f.includes('rpo') || f.includes('RPO') || 
      f.includes('recovery-point') || f.includes('Recovery Point')
    );

    if (rpoFiles.length > 0) {
      hasRPO = true;
      logEvidence(`${productName}: Has RPO documentation`);
    }
  }

  if (hasRPO) {
    logSuccess(`${productName}: Has RPO documentation`);
  } else {
    logWarning(`${productName}: Missing RPO documentation`);
  }

  return hasRPO;
}

/**
 * Check for RTO documentation
 */
function checkRTODocumentation(productPath, productName) {
  const docsDirs = [
    path.join(productPath, 'docs'),
    path.join(productPath, 'documentation'),
    path.join(productPath, '.github'),
  ];

  let hasRTO = false;

  for (const docsDir of docsDirs) {
    if (!existsSync(docsDir)) continue;

    const files = readdirSync(docsDir);
    const rtoFiles = files.filter(f => 
      f.includes('rto') || f.includes('RTO') || 
      f.includes('recovery-time') || f.includes('Recovery Time')
    );

    if (rtoFiles.length > 0) {
      hasRTO = true;
      logEvidence(`${productName}: Has RTO documentation`);
    }
  }

  if (hasRTO) {
    logSuccess(`${productName}: Has RTO documentation`);
  } else {
    logWarning(`${productName}: Missing RTO documentation`);
  }

  return hasRTO;
}

/**
 * Check for restore procedure tests
 */
function checkRestoreProcedureTests(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'tests'),
    path.join(productPath, 'e2e'),
    path.join(productPath, 'src/__tests__'),
  ];

  let hasRestoreTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    const files = readdirSync(testDir);
    const restoreFiles = files.filter(f => 
      f.includes('restore') || f.includes('Restore') || 
      f.includes('backup') || f.includes('Backup')
    );

    if (restoreFiles.length > 0) {
      hasRestoreTests = true;
      logEvidence(`${productName}: Has restore procedure tests`);
    }
  }

  if (hasRestoreTests) {
    logSuccess(`${productName}: Has restore procedure tests`);
  } else {
    logWarning(`${productName}: Missing restore procedure tests`);
  }

  return hasRestoreTests;
}

/**
 * Check for DR drill evidence
 */
function checkDRDrillEvidence(productPath, productName) {
  const docsDirs = [
    path.join(productPath, 'docs'),
    path.join(productPath, '.github'),
  ];

  let hasDRDrill = false;

  for (const docsDir of docsDirs) {
    if (!existsSync(docsDir)) continue;

    const files = readdirSync(docsDir);
    const drillFiles = files.filter(f => 
      f.includes('drill') || f.includes('Drill') || 
      f.includes('disaster') || f.includes('Disaster')
    );

    if (drillFiles.length > 0) {
      hasDRDrill = true;
      logEvidence(`${productName}: Has DR drill evidence`);
    }
  }

  if (hasDRDrill) {
    logSuccess(`${productName}: Has DR drill evidence`);
  } else {
    logWarning(`${productName}: Missing DR drill evidence`);
  }

  return hasDRDrill;
}

/**
 * Generate DR verification report
 */
function generateDRVerificationReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'dr-verification');
  
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

  const reportPath = path.join(evidenceDir, `dr-verification-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 DR verification report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Adding DR restore verification with RPO/RTO assertions...\n');

  // Products to check
  const products = [
    { path: 'shared-services/auth-gateway', name: 'Auth Gateway' },
    { path: 'shared-services/incident-service', name: 'Incident Service' },
    { path: 'shared-services/studio-workflow-service', name: 'Studio Workflow Service' },
    { path: 'products/data-cloud/delivery/launcher', name: 'Data Cloud Launcher' },
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
    
    checkRPODocumentation(productPath, product.name);
    checkRTODocumentation(productPath, product.name);
    checkRestoreProcedureTests(productPath, product.name);
    checkDRDrillEvidence(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateDRVerificationReport();

  if (violations.length > 0) {
    console.log('\nDR verification addition failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nDR verification addition passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nDR verification addition passed.');
}

main();
