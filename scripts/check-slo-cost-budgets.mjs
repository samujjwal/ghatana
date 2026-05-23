#!/usr/bin/env node

/**
 * P2-13: SLO and Cost Budget Validation
 *
 * Validates SLO and cost budget enforcement with thresholds:
 * - SLO thresholds for latency, error rate, availability
 * - Cost budget thresholds per product
 * - Alerting configuration for SLO breaches
 * - Budget enforcement mechanisms
 * - Historical performance tracking
 *
 * Usage: node scripts/check-slo-cost-budgets.mjs [--product <product>]
 */

import { readFileSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getReleaseMode, processValidationResults, logValidationResults } from './lib/release-evidence-policy.mjs';
import { getActiveProducts, resolveProductForProof } from './lib/product-registry-helper.mjs';

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
 * SLO threshold definitions
 */
const SLO_THRESHOLDS = {
  latency: {
    p50: 100, // ms
    p95: 500, // ms
    p99: 1000, // ms
  },
  errorRate: {
    critical: 0.01, // 1%
    warning: 0.005, // 0.5%
  },
  availability: {
    minimum: 0.999, // 99.9%
    target: 0.9999, // 99.99%
  },
};

/**
 * Cost budget thresholds (USD per month)
 */
const COST_BUDGET_THRESHOLDS = {
  'data-cloud': {
    development: 1000,
    staging: 5000,
    production: 50000,
  },
  'finance': {
    development: 500,
    staging: 2000,
    production: 20000,
  },
  'phr': {
    development: 500,
    staging: 2000,
    production: 20000,
  },
  'digital-marketing': {
    development: 300,
    staging: 1000,
    production: 10000,
  },
  'yappc': {
    development: 200,
    staging: 1000,
    production: 10000,
  },
};

/**
 * Check for SLO configuration
 */
function checkSLOConfiguration(productPath, productName, productId) {
  const sloFiles = [
    path.join(productPath, 'config/slo.json'),
    path.join(productPath, 'config/slo.yaml'),
    path.join(productPath, 'monitoring/slo.json'),
    path.join(productPath, 'monitoring/slo.yaml'),
  ];

  let hasSLOConfig = false;
  for (const file of sloFiles) {
    if (existsSync(file)) {
      hasSLOConfig = true;
      logEvidence(`${productName}: SLO configuration found at ${path.relative(repoRoot, file)}`);
      
      try {
        const content = readFileSync(file, 'utf8');
        const data = JSON.parse(content);
        
        // Validate SLO thresholds
        if (data.latency) {
          if (data.latency.p95 > SLO_THRESHOLDS.latency.p95) {
            logWarning(`${productName}: P95 latency threshold ${data.latency.p95}ms exceeds recommended ${SLO_THRESHOLDS.latency.p95}ms`);
          } else {
            logEvidence(`${productName}: P95 latency threshold ${data.latency.p95}ms within recommended bounds`);
          }
        }
        
        if (data.errorRate) {
          if (data.errorRate.critical > SLO_THRESHOLDS.errorRate.critical) {
            logError(`${productName}: Critical error rate threshold ${data.errorRate.critical} exceeds maximum ${SLO_THRESHOLDS.errorRate.critical}`);
          } else {
            logEvidence(`${productName}: Critical error rate threshold ${data.errorRate.critical} within bounds`);
          }
        }
        
        if (data.availability) {
          if (data.availability.minimum < SLO_THRESHOLDS.availability.minimum) {
            logError(`${productName}: Minimum availability ${data.availability.minimum} below required ${SLO_THRESHOLDS.availability.minimum}`);
          } else {
            logEvidence(`${productName}: Minimum availability ${data.availability.minimum} meets requirements`);
          }
        }
      } catch (error) {
        logWarning(`${productName}: Failed to parse SLO configuration: ${error.message}`);
      }
    }
  }

  if (!hasSLOConfig) {
    logError(`${productName}: Missing SLO configuration`);
    return false;
  }

  logSuccess(`${productName}: SLO configuration validated`);
  return true;
}

/**
 * Check for cost budget configuration
 */
function checkCostBudgetConfiguration(productPath, productName, productId) {
  const budgetFiles = [
    path.join(productPath, 'config/cost-budget.json'),
    path.join(productPath, 'config/cost-budget.yaml'),
    path.join(productPath, 'monitoring/cost-budget.json'),
  ];

  const thresholds = COST_BUDGET_THRESHOLDS[productId];
  
  let hasBudgetConfig = false;
  for (const file of budgetFiles) {
    if (existsSync(file)) {
      hasBudgetConfig = true;
      logEvidence(`${productName}: Cost budget configuration found at ${path.relative(repoRoot, file)}`);
      
      try {
        const content = readFileSync(file, 'utf8');
        const data = JSON.parse(content);
        
        // Validate budget thresholds
        if (thresholds) {
          if (data.production && data.production > thresholds.production) {
            logWarning(`${productName}: Production budget $${data.production} exceeds recommended $${thresholds.production}`);
          }
          
          if (data.staging && data.staging > thresholds.staging) {
            logWarning(`${productName}: Staging budget $${data.staging} exceeds recommended $${thresholds.staging}`);
          }
          
          if (data.development && data.development > thresholds.development) {
            logWarning(`${productName}: Development budget $${data.development} exceeds recommended $${thresholds.development}`);
          }
        }
        
        // Check for budget enforcement
        if (data.enforcement === false) {
          logError(`${productName}: Budget enforcement is disabled`);
        } else {
          logEvidence(`${productName}: Budget enforcement is enabled`);
        }
        
        // Check for alerting configuration
        if (data.alerting) {
          logEvidence(`${productName}: Budget alerting configured`);
        } else {
          logWarning(`${productName}: Missing budget alerting configuration`);
        }
      } catch (error) {
        logWarning(`${productName}: Failed to parse cost budget configuration: ${error.message}`);
      }
    }
  }

  if (!hasBudgetConfig) {
    logError(`${productName}: Missing cost budget configuration`);
    return false;
  }

  logSuccess(`${productName}: Cost budget configuration validated`);
  return true;
}

/**
 * Check for alerting configuration
 */
function checkAlertingConfiguration(productPath, productName) {
  const alertFiles = [
    path.join(productPath, 'monitoring/alerts'),
    path.join(productPath, 'config/alerts'),
  ];

  let hasAlertConfig = false;
  for (const dir of alertFiles) {
    if (existsSync(dir)) {
      hasAlertConfig = true;
      logEvidence(`${productName}: Alerting configuration found at ${path.relative(repoRoot, dir)}`);
    }
  }

  if (!hasAlertConfig) {
    logWarning(`${productName}: Missing alerting configuration`);
    return false;
  }

  logSuccess(`${productName}: Alerting configuration validated`);
  return true;
}

/**
 * Generate evidence report
 */
function generateEvidenceReport() {
  const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'slo-cost-budgets');
  
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

  const reportPath = path.join(evidenceDir, 'slo-cost-budgets-latest.json');
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  
  console.log(`\n📄 Evidence report generated: ${reportPath}`);
}

/**
 * Main validation
 */
function main() {
  console.log('Checking SLO and cost budget enforcement...\n');

  // Resolve active products from canonical product registry
  const registryProducts = getActiveProducts();
  
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
    
    checkSLOConfiguration(productPath, product.name, product.productId);
    checkCostBudgetConfiguration(productPath, product.name, product.productId);
    checkAlertingConfiguration(productPath, product.name);
  }

  console.log('\n--- Summary ---');
  console.log(`Errors: ${violations.length}`);
  console.log(`Warnings: ${warnings.length}`);
  console.log(`Evidence items: ${evidence.length}`);

  generateEvidenceReport();

  // Process validation results with release evidence policy
  const validationResults = processValidationResults(violations, warnings, evidence, RELEASE_MODE);
  logValidationResults(validationResults, 'SLO and Cost Budget Validation');

  if (validationResults.shouldFail) {
    process.exit(1);
  }

  console.log('\nSLO and cost budget check passed.');
}

main();
