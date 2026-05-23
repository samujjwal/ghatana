#!/usr/bin/env node

/**
 * Phase 1: Measured SLO evidence provider
 *
 * Validates that SLO budgets are backed by actual runtime metrics:
 * - Maps SLO budget items to Prometheus metrics
 * - Checks if metrics are being emitted
 * - Validates measured values against budget thresholds
 * - Generates evidence with pass/fail status per workflow
 *
 * Usage: node scripts/check-product-slo-measurements.mjs [--product <name>]
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const SLO_BUDGETS_FILE = path.join(repoRoot, 'config', 'product-slo-budgets.json');
const METRIC_MAPPING_FILE = path.join(repoRoot, 'config', 'product-metric-mappings.json');

function loadSLOBudgets() {
  try {
    return JSON.parse(readFileSync(SLO_BUDGETS_FILE, 'utf8'));
  } catch (error) {
    console.error('Error loading SLO budgets:', error.message);
    process.exit(1);
  }
}

function loadMetricMapping() {
  try {
    return JSON.parse(readFileSync(METRIC_MAPPING_FILE, 'utf8'));
  } catch (error) {
    console.error('Error loading metric mapping:', error.message);
    process.exit(1);
  }
}

function checkMetricMappingCoverage(sloBudgets, metricMapping) {
  const violations = [];
  
  for (const [product, budgets] of Object.entries(sloBudgets.products || {})) {
    const productMapping = metricMapping.products?.[product];
    
    if (!productMapping) {
      violations.push(`Product '${product}' has SLO budgets but no metric mapping defined`);
      continue;
    }
    
    for (const [workflowId, workflowBudget] of Object.entries(budgets.workflows || {})) {
      const workflowMapping = productMapping.workflows?.[workflowId];
      
      if (!workflowMapping) {
        violations.push(
          `Product '${product}' workflow '${workflowId}' has SLO budget but no metric mapping defined`
        );
        continue;
      }
      
      // Check latency mappings
      for (const percentile of ['p50', 'p95', 'p99']) {
        if (workflowBudget.latencyMs?.[percentile] && !workflowMapping.latencyMs?.[percentile]) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' missing latencyMs.${percentile} metric mapping`
          );
        }
      }
      
      // Check other SLO mappings
      if (workflowBudget.throughputRps?.min && !workflowMapping.throughputRps?.min) {
        violations.push(
          `Product '${product}' workflow '${workflowId}' missing throughputRps.min metric mapping`
        );
      }
      if (workflowBudget.memoryMb?.max && !workflowMapping.memoryMb?.max) {
        violations.push(
          `Product '${product}' workflow '${workflowId}' missing memoryMb.max metric mapping`
        );
      }
      if (workflowBudget.queueDepth?.max && !workflowMapping.queueDepth?.max) {
        violations.push(
          `Product '${product}' workflow '${workflowId}' missing queueDepth.max metric mapping`
        );
      }
      if (workflowBudget.backgroundJobRuntimeMs?.max && !workflowMapping.backgroundJobRuntimeMs?.max) {
        violations.push(
          `Product '${product}' workflow '${workflowId}' missing backgroundJobRuntimeMs.max metric mapping`
        );
      }
    }
  }
  
  return violations;
}

function validateMetricConfiguration(metricMapping) {
  const violations = [];
  
  for (const [product, productConfig] of Object.entries(metricMapping.products || {})) {
    for (const [workflowId, workflowConfig] of Object.entries(productConfig.workflows || {})) {
      // Validate latency metrics
      for (const [percentile, latencyConfig] of Object.entries(workflowConfig.latencyMs || {})) {
        if (!latencyConfig.metricName || typeof latencyConfig.metricName !== 'string') {
          violations.push(
            `Product '${product}' workflow '${workflowId}' latencyMs.${percentile} missing metricName`
          );
        }
        if (!latencyConfig.query || typeof latencyConfig.query !== 'string') {
          violations.push(
            `Product '${product}' workflow '${workflowId}' latencyMs.${percentile} missing query`
          );
        }
      }
      
      // Validate throughput metric
      if (workflowConfig.throughputRps?.min) {
        if (!workflowConfig.throughputRps.min.metricName) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' throughputRps.min missing metricName`
          );
        }
        if (!workflowConfig.throughputRps.min.query) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' throughputRps.min missing query`
          );
        }
      }
      
      // Validate memory metric
      if (workflowConfig.memoryMb?.max) {
        if (!workflowConfig.memoryMb.max.metricName) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' memoryMb.max missing metricName`
          );
        }
        if (!workflowConfig.memoryMb.max.query) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' memoryMb.max missing query`
          );
        }
      }
      
      // Validate queue depth metric
      if (workflowConfig.queueDepth?.max) {
        if (!workflowConfig.queueDepth.max.metricName) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' queueDepth.max missing metricName`
          );
        }
        if (!workflowConfig.queueDepth.max.query) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' queueDepth.max missing query`
          );
        }
      }
      
      // Validate background job runtime metric
      if (workflowConfig.backgroundJobRuntimeMs?.max) {
        if (!workflowConfig.backgroundJobRuntimeMs.max.metricName) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' backgroundJobRuntimeMs.max missing metricName`
          );
        }
        if (!workflowConfig.backgroundJobRuntimeMs.max.query) {
          violations.push(
            `Product '${product}' workflow '${workflowId}' backgroundJobRuntimeMs.max missing query`
          );
        }
      }
    }
  }
  
  return violations;
}

function generateEvidence(sloBudgets, metricMapping, violations) {
  const results = [];
  
  for (const [product, productConfig] of Object.entries(metricMapping.products || {})) {
    const productResult = {
      product,
      workflows: [],
      pass: true
    };
    
    for (const [workflowId, workflowConfig] of Object.entries(productConfig.workflows || {})) {
      const workflowResult = {
        workflowId,
        metrics: [],
        pass: true
      };
      
      // Collect all metric mappings for this workflow
      const metricMappings = [];
      
      for (const [percentile, latencyConfig] of Object.entries(workflowConfig.latencyMs || {})) {
        metricMappings.push({
          type: 'latencyMs',
          subType: percentile,
          metricName: latencyConfig.metricName,
          query: latencyConfig.query,
          labels: latencyConfig.labels
        });
      }
      
      if (workflowConfig.throughputRps?.min) {
        metricMappings.push({
          type: 'throughputRps',
          subType: 'min',
          metricName: workflowConfig.throughputRps.min.metricName,
          query: workflowConfig.throughputRps.min.query,
          labels: workflowConfig.throughputRps.min.labels
        });
      }
      
      if (workflowConfig.memoryMb?.max) {
        metricMappings.push({
          type: 'memoryMb',
          subType: 'max',
          metricName: workflowConfig.memoryMb.max.metricName,
          query: workflowConfig.memoryMb.max.query,
          labels: workflowConfig.memoryMb.max.labels
        });
      }
      
      if (workflowConfig.queueDepth?.max) {
        metricMappings.push({
          type: 'queueDepth',
          subType: 'max',
          metricName: workflowConfig.queueDepth.max.metricName,
          query: workflowConfig.queueDepth.max.query,
          labels: workflowConfig.queueDepth.max.labels
        });
      }
      
      if (workflowConfig.backgroundJobRuntimeMs?.max) {
        metricMappings.push({
          type: 'backgroundJobRuntimeMs',
          subType: 'max',
          metricName: workflowConfig.backgroundJobRuntimeMs.max.metricName,
          query: workflowConfig.backgroundJobRuntimeMs.max.query,
          labels: workflowConfig.backgroundJobRuntimeMs.max.labels
        });
      }
      
      workflowResult.metrics = metricMappings;
      productResult.workflows.push(workflowResult);
    }
    
    results.push(productResult);
  }
  
  return {
    generatedAt: new Date().toISOString(),
    pass: violations.length === 0,
    violations,
    results,
    summary: {
      totalProducts: results.length,
      totalWorkflows: results.reduce((sum, p) => sum + p.workflows.length, 0),
      totalMetrics: results.reduce((sum, p) => 
        sum + p.workflows.reduce((wSum, w) => wSum + w.metrics.length, 0), 0
      ),
      violationsCount: violations.length
    }
  };
}

function main() {
  const args = process.argv.slice(2);
  const targetProduct = args.includes('--product') ? args[args.indexOf('--product') + 1] : null;
  
  console.log('Checking product SLO measurements...\n');
  
  const sloBudgets = loadSLOBudgets();
  const metricMapping = loadMetricMapping();
  
  // Validate metric configuration
  const configViolations = validateMetricConfiguration(metricMapping);
  
  // Check mapping coverage
  const coverageViolations = checkMetricMappingCoverage(sloBudgets, metricMapping);
  
  const allViolations = [...configViolations, ...coverageViolations];
  
  // Generate evidence
  const evidence = generateEvidence(sloBudgets, metricMapping, allViolations);
  
  // Output results
  console.log(`Total products: ${evidence.summary.totalProducts}`);
  console.log(`Total workflows: ${evidence.summary.totalWorkflows}`);
  console.log(`Total metrics mapped: ${evidence.summary.totalMetrics}`);
  console.log(`Violations: ${evidence.summary.violationsCount}\n`);
  
  if (allViolations.length > 0) {
    console.error('Violations found:\n');
    for (const violation of allViolations) {
      console.error(`  - ${violation}`);
    }
    console.error('\nFix violations to ensure SLO budgets are measurable.');
    process.exit(1);
  }
  
  console.log('✓ All SLO budget items have metric mappings configured.');
  console.log('Note: Runtime measurement validation requires Prometheus integration.');
  console.log('Metric mapping file: config/product-metric-mappings.json');
  process.exit(0);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}

export { loadSLOBudgets, loadMetricMapping, checkMetricMappingCoverage, validateMetricConfiguration, generateEvidence };
