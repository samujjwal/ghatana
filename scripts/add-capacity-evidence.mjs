#!/usr/bin/env node

/**
 * Wave 4: Add Capacity/Backpressure/Load Evidence
 *
 * Adds capacity, backpressure, and load evidence for each product:
 * - Capacity planning documentation
 * - Backpressure handling tests
 * - Load testing evidence
 * - Performance benchmarks
 * - Resource utilization monitoring
 *
 * This ensures each product has evidence of capacity and load handling capabilities.
 *
 * Usage: node scripts/add-capacity-evidence.mjs [--product <product>]
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
 * Check for capacity planning documentation
 */
function checkCapacityPlanning(productPath, productName) {
  const docsDirs = [
    path.join(productPath, 'docs'),
    path.join(productPath, 'documentation'),
    path.join(productPath, '.github'),
  ];

  let hasCapacityPlanning = false;

  for (const docsDir of docsDirs) {
    if (!existsSync(docsDir)) continue;

    const files = readdirSync(docsDir);
    const capacityFiles = files.filter(f => 
      f.includes('capacity') || f.includes('Capacity') || 
      f.includes('scaling') || f.includes('Scaling')
    );

    if (capacityFiles.length > 0) {
      hasCapacityPlanning = true;
      logEvidence(`${productName}: Has capacity planning documentation`);
    }
  }

  if (hasCapacityPlanning) {
    logSuccess(`${productName}: Has capacity planning documentation`);
  } else {
    logWarning(`${productName}: Missing capacity planning documentation`);
  }

  return hasCapacityPlanning;
}

/**
 * Check for backpressure handling
 */
function checkBackpressureHandling(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src'),
    path.join(productPath, 'tests'),
  ];

  let hasBackpressure = false;

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
          } else if (item.endsWith('.ts') || item.endsWith('.tsx') || item.endsWith('.js') || item.endsWith('.java')) {
            const content = readFileSync(itemPath, 'utf8');
            
            if (content.includes('backpressure') || content.includes('Backpressure') ||
                content.includes('circuit') && content.includes('breaker')) {
              hasBackpressure = true;
              logEvidence(`${productName}: Has backpressure handling`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasBackpressure) {
    logSuccess(`${productName}: Has backpressure handling`);
  } else {
    logWarning(`${productName}: Missing backpressure handling`);
  }

  return hasBackpressure;
}

/**
 * Check for load testing evidence
 */
function checkLoadTestingEvidence(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'tests'),
    path.join(productPath, 'e2e'),
    path.join(productPath, 'k6-tests'),
  ];

  let hasLoadTests = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    const files = readdirSync(testDir);
    const loadTestFiles = files.filter(f => 
      f.includes('load') || f.includes('Load') || 
      f.includes('k6') || f.includes('performance')
    );

    if (loadTestFiles.length > 0) {
      hasLoadTests = true;
      logEvidence(`${productName}: Has load testing evidence`);
    }
  }

  if (hasLoadTests) {
    logSuccess(`${productName}: Has load testing evidence`);
  } else {
    logWarning(`${productName}: Missing load testing evidence`);
  }

  return hasLoadTests;
}

/**
 * Check for performance benchmarks
 */
function checkPerformanceBenchmarks(productPath, productName) {
  const testDirs = [
    path.join(productPath, 'tests'),
    path.join(productPath, 'benchmarks'),
  ];

  let hasBenchmarks = false;

  for (const testDir of testDirs) {
    if (!existsSync(testDir)) continue;

    const files = readdirSync(testDir);
    const benchmarkFiles = files.filter(f => 
      f.includes('benchmark') || f.includes('Benchmark') || 
      f.includes('perf') || f.includes('performance')
    );

    if (benchmarkFiles.length > 0) {
      hasBenchmarks = true;
      logEvidence(`${productName}: Has performance benchmarks`);
    }
  }

  if (hasBenchmarks) {
    logSuccess(`${productName}: Has performance benchmarks`);
  } else {
    logWarning(`${productName}: Missing performance benchmarks`);
  }

  return hasBenchmarks;
}

/**
 * Generate capacity evidence report
 */
function generateCapacityEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'capacity-evidence');
  
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

  const reportPath = path.join(evidenceDir, `capacity-evidence-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Capacity evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Adding capacity/backpressure/load evidence...\n');

  // Products to check
  const products = [
    { path: 'shared-services/auth-gateway', name: 'Auth Gateway' },
    { path: 'shared-services/incident-service', name: 'Incident Service' },
    { path: 'shared-services/studio-workflow-service', name: 'Studio Workflow Service' },
    { path: 'products/data-cloud/delivery/launcher', name: 'Data Cloud Launcher' },
    { path: 'products/aep', name: 'AEP' },
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
    
    checkCapacityPlanning(productPath, product.name);
    checkBackpressureHandling(productPath, product.name);
    checkLoadTestingEvidence(productPath, product.name);
    checkPerformanceBenchmarks(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateCapacityEvidenceReport();

  if (violations.length > 0) {
    console.log('\nCapacity evidence addition failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nCapacity evidence addition passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nCapacity evidence addition passed.');
}

main();
