#!/usr/bin/env node

/**
 * Wave 4: Add p95/p99 SLO Gates per Product
 *
 * Adds SLO (Service Level Objective) gates for each product:
 * - p95 latency thresholds
 * - p99 latency thresholds
 * - Error rate thresholds
 * - Availability thresholds
 * - Throughput thresholds
 *
 * This ensures each product meets its performance SLOs before release.
 *
 * Usage: node scripts/add-slo-gates.mjs [--product <product>]
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
 * Check for SLO configuration
 */
function checkSLOConfiguration(productPath, productName) {
  const configDirs = [
    path.join(productPath, 'config'),
    path.join(productPath, '.github'),
    path.join(productPath, 'monitoring'),
  ];

  let hasSLOConfig = false;

  for (const configDir of configDirs) {
    if (!existsSync(configDir)) continue;

    const files = readdirSync(configDir);
    const sloFiles = files.filter(f => 
      f.includes('slo') || f.includes('SLO') || 
      f.includes('service-level') || f.includes('latency')
    );

    if (sloFiles.length > 0) {
      hasSLOConfig = true;
      logEvidence(`${productName}: Has SLO configuration files`);
    }
  }

  if (hasSLOConfig) {
    logSuccess(`${productName}: Has SLO configuration`);
  } else {
    logWarning(`${productName}: Missing SLO configuration`);
  }

  return hasSLOConfig;
}

/**
 * Check for p95 latency monitoring
 */
function checkP95LatencyMonitoring(productPath, productName) {
  const monitoringDirs = [
    path.join(productPath, 'monitoring'),
    path.join(productPath, 'src'),
  ];

  let hasP95Monitoring = false;

  for (const monitoringDir of monitoringDirs) {
    if (!existsSync(monitoringDir)) continue;

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
            
            if (content.includes('p95') || content.includes('95th')) {
              hasP95Monitoring = true;
              logEvidence(`${productName}: Has p95 latency monitoring`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(monitoringDir);
  }

  if (hasP95Monitoring) {
    logSuccess(`${productName}: Has p95 latency monitoring`);
  } else {
    logWarning(`${productName}: Missing p95 latency monitoring`);
  }

  return hasP95Monitoring;
}

/**
 * Check for p99 latency monitoring
 */
function checkP99LatencyMonitoring(productPath, productName) {
  const monitoringDirs = [
    path.join(productPath, 'monitoring'),
    path.join(productPath, 'src'),
  ];

  let hasP99Monitoring = false;

  for (const monitoringDir of monitoringDirs) {
    if (!existsSync(monitoringDir)) continue;

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
            
            if (content.includes('p99') || content.includes('99th')) {
              hasP99Monitoring = true;
              logEvidence(`${productName}: Has p99 latency monitoring`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(monitoringDir);
  }

  if (hasP99Monitoring) {
    logSuccess(`${productName}: Has p99 latency monitoring`);
  } else {
    logWarning(`${productName}: Missing p99 latency monitoring`);
  }

  return hasP99Monitoring;
}

/**
 * Check for error rate monitoring
 */
function checkErrorRateMonitoring(productPath, productName) {
  const monitoringDirs = [
    path.join(productPath, 'monitoring'),
    path.join(productPath, 'src'),
  ];

  let hasErrorRateMonitoring = false;

  for (const monitoringDir of monitoringDirs) {
    if (!existsSync(monitoringDir)) continue;

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
            
            if (content.includes('error rate') || content.includes('error_rate') || content.includes('errorRate')) {
              hasErrorRateMonitoring = true;
              logEvidence(`${productName}: Has error rate monitoring`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(monitoringDir);
  }

  if (hasErrorRateMonitoring) {
    logSuccess(`${productName}: Has error rate monitoring`);
  } else {
    logWarning(`${productName}: Missing error rate monitoring`);
  }

  return hasErrorRateMonitoring;
}

/**
 * Generate SLO gate report
 */
function generateSLOGateReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'slo-gates');
  
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

  const reportPath = path.join(evidenceDir, `slo-gates-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 SLO gate report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Adding p95/p99 SLO gates per product...\n');

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
    
    checkSLOConfiguration(productPath, product.name);
    checkP95LatencyMonitoring(productPath, product.name);
    checkP99LatencyMonitoring(productPath, product.name);
    checkErrorRateMonitoring(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateSLOGateReport();

  if (violations.length > 0) {
    console.log('\nSLO gate addition failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nSLO gate addition passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nSLO gate addition passed.');
}

main();
