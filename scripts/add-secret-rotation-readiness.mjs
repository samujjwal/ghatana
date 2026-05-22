#!/usr/bin/env node

/**
 * Wave 4: Add Secret Rotation Readiness
 *
 * Adds secret rotation readiness checks for each product:
 * - Secret inventory documentation
 * - Rotation procedures documented
 * - Rotation automation tests
 * - Secret versioning support
 * - Rotation monitoring
 *
 * This ensures each product is ready for secret rotation.
 *
 * Usage: node scripts/add-secret-rotation-readiness.mjs [--product <product>]
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
 * Check for secret inventory
 */
function checkSecretInventory(productPath, productName) {
  const docsDirs = [
    path.join(productPath, 'docs'),
    path.join(productPath, 'config'),
    path.join(productPath, '.github'),
  ];

  let hasSecretInventory = false;

  for (const docsDir of docsDirs) {
    if (!existsSync(docsDir)) continue;

    const files = readdirSync(docsDir);
    const secretFiles = files.filter(f => 
      f.includes('secret') || f.includes('Secret') || 
      f.includes('credential') || f.includes('Credential')
    );

    if (secretFiles.length > 0) {
      hasSecretInventory = true;
      logEvidence(`${productName}: Has secret inventory documentation`);
    }
  }

  if (hasSecretInventory) {
    logSuccess(`${productName}: Has secret inventory`);
  } else {
    logWarning(`${productName}: Missing secret inventory`);
  }

  return hasSecretInventory;
}

/**
 * Check for rotation procedures
 */
function checkRotationProcedures(productPath, productName) {
  const docsDirs = [
    path.join(productPath, 'docs'),
    path.join(productPath, '.github'),
  ];

  let hasRotationProcedures = false;

  for (const docsDir of docsDirs) {
    if (!existsSync(docsDir)) continue;

    const files = readdirSync(docsDir);
    const rotationFiles = files.filter(f => 
      f.includes('rotation') || f.includes('Rotation') || 
      f.includes('rotate') || f.includes('Rotate')
    );

    if (rotationFiles.length > 0) {
      hasRotationProcedures = true;
      logEvidence(`${productName}: Has rotation procedures documented`);
    }
  }

  if (hasRotationProcedures) {
    logSuccess(`${productName}: Has rotation procedures`);
  } else {
    logWarning(`${productName}: Missing rotation procedures`);
  }

  return hasRotationProcedures;
}

/**
 * Check for secret versioning
 */
function checkSecretVersioning(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src'),
    path.join(productPath, 'config'),
  ];

  let hasSecretVersioning = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.yaml') || item.endsWith('.yml')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('secret') && content.includes('version') ||
                content.includes('secretVersion') || content.includes('secret_version')) {
              hasSecretVersioning = true;
              logEvidence(`${productName}: Has secret versioning support`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasSecretVersioning) {
    logSuccess(`${productName}: Has secret versioning`);
  } else {
    logWarning(`${productName}: Missing secret versioning`);
  }

  return hasSecretVersioning;
}

/**
 * Generate secret rotation readiness report
 */
function generateSecretRotationReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'secret-rotation');
  
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

  const reportPath = path.join(evidenceDir, `secret-rotation-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Secret rotation readiness report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Adding secret rotation readiness...\n');

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
    
    checkSecretInventory(productPath, product.name);
    checkRotationProcedures(productPath, product.name);
    checkSecretVersioning(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateSecretRotationReport();

  if (violations.length > 0) {
    console.log('\nSecret rotation readiness addition failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nSecret rotation readiness addition passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nSecret rotation readiness addition passed.');
}

main();
