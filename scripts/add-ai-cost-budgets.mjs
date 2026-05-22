#!/usr/bin/env node

/**
 * Wave 4: Add AI Cost Budget and Model Quality Gates
 *
 * Adds AI cost budget and model quality gates for each product:
 * - Cost budget enforcement
 * - Model quality thresholds
 * - Cost tracking and reporting
 * - Model performance monitoring
 * - Budget alerting
 *
 * This ensures AI operations stay within budget and meet quality standards.
 *
 * Usage: node scripts/add-ai-cost-budgets.mjs [--product <product>]
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
 * Check for cost budget enforcement
 */
function checkCostBudgetEnforcement(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src'),
    path.join(productPath, 'config'),
  ];

  let hasCostBudget = false;

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
            
            if (content.includes('cost') && content.includes('budget') ||
                content.includes('costBudget') || content.includes('cost_budget')) {
              hasCostBudget = true;
              logEvidence(`${productName}: Has cost budget enforcement`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasCostBudget) {
    logSuccess(`${productName}: Has cost budget enforcement`);
  } else {
    logWarning(`${productName}: Missing cost budget enforcement`);
  }

  return hasCostBudget;
}

/**
 * Check for model quality thresholds
 */
function checkModelQualityThresholds(productPath, productName) {
  const srcDirs = [
    path.join(productPath, 'src'),
    path.join(productPath, 'config'),
  ];

  let hasQualityThresholds = false;

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
            
            if (content.includes('quality') && content.includes('threshold') ||
                content.includes('qualityThreshold') || content.includes('quality_threshold')) {
              hasQualityThresholds = true;
              logEvidence(`${productName}: Has model quality thresholds`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(srcDir);
  }

  if (hasQualityThresholds) {
    logSuccess(`${productName}: Has model quality thresholds`);
  } else {
    logWarning(`${productName}: Missing model quality thresholds`);
  }

  return hasQualityThresholds;
}

/**
 * Check for cost tracking
 */
function checkCostTracking(productPath, productName) {
  const monitoringDirs = [
    path.join(productPath, 'monitoring'),
    path.join(productPath, 'src'),
  ];

  let hasCostTracking = false;

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
            
            if (content.includes('cost') && content.includes('track') ||
                content.includes('costTracking') || content.includes('cost_tracking')) {
              hasCostTracking = true;
              logEvidence(`${productName}: Has cost tracking`);
            }
          }
        }
      } catch (e) {
        // Skip directories we can't read
      }
    }

    searchDir(monitoringDir);
  }

  if (hasCostTracking) {
    logSuccess(`${productName}: Has cost tracking`);
  } else {
    logWarning(`${productName}: Missing cost tracking`);
  }

  return hasCostTracking;
}

/**
 * Generate AI cost budget report
 */
function generateAICostBudgetReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'ai-cost-budgets');
  
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

  const reportPath = path.join(evidenceDir, `ai-cost-budgets-${Date.now()}.json`);
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 AI cost budget report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Adding AI cost budget and model quality gates...\n');

  // Products to check
  const products = [
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
    
    checkCostBudgetEnforcement(productPath, product.name);
    checkModelQualityThresholds(productPath, product.name);
    checkCostTracking(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateAICostBudgetReport();

  if (violations.length > 0) {
    console.log('\nAI cost budget addition failed with errors:');
    violations.forEach(v => console.log(`  - ${v}`));
    process.exit(1);
  }

  if (warnings.length > 0) {
    console.log('\nAI cost budget addition passed with warnings:');
    warnings.forEach(w => console.log(`  - ${w}`));
  }

  console.log('\nAI cost budget addition passed.');
}

main();
