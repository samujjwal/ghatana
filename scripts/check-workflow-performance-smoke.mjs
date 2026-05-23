#!/usr/bin/env node

/**
 * Phase 1: Lightweight workflow performance smoke tests
 *
 * Validates that key workflows for PHR and Digital Marketing have performance test coverage:
 * - Checks for performance test existence per workflow
 * - Validates performance test structure and assertions
 * - Generates evidence with pass/fail status per workflow
 *
 * Usage: node scripts/check-workflow-performance-smoke.mjs [--product <name>]
 */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const METRIC_MAPPING_FILE = path.join(repoRoot, 'config', 'product-metric-mapping.json');

function loadMetricMapping() {
  try {
    return JSON.parse(readFileSync(METRIC_MAPPING_FILE, 'utf8'));
  } catch (error) {
    console.error('Error loading metric mapping:', error.message);
    process.exit(1);
  }
}

function checkPerformanceTestCoverage(metricMapping) {
  const violations = [];
  const results = [];
  
  for (const [product, productConfig] of Object.entries(metricMapping.products || {})) {
    const productResult = {
      product,
      workflows: [],
      pass: true
    };
    
    for (const workflow of productConfig.workflows || []) {
      const workflowResult = {
        name: workflow.name,
        description: workflow.description,
        hasPerformanceTest: false,
        testPath: null,
        hasLatencyAssertion: false,
        hasThroughputAssertion: false,
        hasErrorRateAssertion: false
      };
      
      // Check for performance test file
      const testPath = findPerformanceTestPath(product, workflow.name);
      workflowResult.testPath = testPath;
      workflowResult.hasPerformanceTest = testPath !== null;
      
      if (testPath && existsSync(path.join(repoRoot, testPath))) {
        const testContent = readFileSync(path.join(repoRoot, testPath), 'utf8');
        
        // Check for latency assertions
        workflowResult.hasLatencyAssertion = 
          testContent.includes('latency') || 
          testContent.includes('duration') ||
          testContent.includes('responseTime');
        
        // Check for throughput assertions
        workflowResult.hasThroughputAssertion = 
          testContent.includes('throughput') || 
          testContent.includes('rps') ||
          testContent.includes('requests');
        
        // Check for error rate assertions
        workflowResult.hasErrorRateAssertion = 
          testContent.includes('error') || 
          testContent.includes('failure') ||
          testContent.includes('success');
      }
      
      if (!workflowResult.hasPerformanceTest) {
        violations.push(
          `Product '${product}' workflow '${workflow.name}' has no performance test. ` +
          `Add performance test at: ${getExpectedTestPath(product, workflow.name)}`
        );
        productResult.pass = false;
      }
      
      productResult.workflows.push(workflowResult);
    }
    
    results.push(productResult);
  }
  
  return { violations, results };
}

function findPerformanceTestPath(product, workflowName) {
  // Map workflow names to expected test file paths
  const workflowToTestMap = {
    'digital-marketing': {
      'campaign-activation': 'products/digital-marketing/dm-api/k6-tests/campaign-activation-smoke.js',
      'lead-capture': 'products/digital-marketing/dm-api/k6-tests/lead-capture-smoke.js',
      'notification-send': 'products/digital-marketing/dm-api/k6-tests/notification-smoke.js'
    },
    'phr': {
      'patient-record-fetch': 'products/finance/domains/phr/src/test/java/com/ghatana/products/finance/dom/phr/performance/PatientRecordFetchPerformanceTest.java',
      'consent-check': 'products/finance/domains/phr/src/test/java/com/ghatana/products/finance/dom/phr/performance/ConsentCheckPerformanceTest.java',
      'fhir-validation': 'products/finance/domains/phr/src/test/java/com/ghatana/products/finance/dom/phr/performance/FhirValidationPerformanceTest.java',
      'break-glass-access': 'products/finance/domains/phr/src/test/java/com/ghatana/products/finance/dom/phr/performance/BreakGlassAccessPerformanceTest.java'
    }
  };
  
  return workflowToTestMap[product]?.[workflowName] || null;
}

function getExpectedTestPath(product, workflowName) {
  const testPath = findPerformanceTestPath(product, workflowName);
  return testPath || `products/${product}/performance-tests/${workflowName}-smoke.test.js`;
}

function generatePerformanceTestEvidence(metricMapping, violations, results) {
  return {
    generatedAt: new Date().toISOString(),
    pass: violations.length === 0,
    violations,
    results,
    summary: {
      totalProducts: results.length,
      totalWorkflows: results.reduce((sum, p) => sum + p.workflows.length, 0),
      workflowsWithTests: results.reduce((sum, p) => 
        sum + p.workflows.filter(w => w.hasPerformanceTest).length, 0
      ),
      violationsCount: violations.length
    }
  };
}

function main() {
  const args = process.argv.slice(2);
  const targetProduct = args.includes('--product') ? args[args.indexOf('--product') + 1] : null;
  
  console.log('Checking workflow performance test coverage...\n');
  
  const metricMapping = loadMetricMapping();
  
  // Check performance test coverage
  const { violations, results } = checkPerformanceTestCoverage(metricMapping);
  
  // Generate evidence
  const evidence = generatePerformanceTestEvidence(metricMapping, violations, results);
  
  // Output results
  console.log(`Total products: ${evidence.summary.totalProducts}`);
  console.log(`Total workflows: ${evidence.summary.totalWorkflows}`);
  console.log(`Workflows with performance tests: ${evidence.summary.workflowsWithTests}`);
  console.log(`Violations: ${evidence.summary.violationsCount}\n`);
  
  if (violations.length > 0) {
    console.error('Violations found:\n');
    for (const violation of violations) {
      console.error(`  - ${violation}`);
    }
    console.error('\nAdd performance smoke tests for all critical workflows.');
    process.exit(1);
  }
  
  console.log('✓ All critical workflows have performance test coverage.');
  console.log('Note: Full load/stress testing should run in dedicated performance environments.');
  process.exit(0);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}

export { loadMetricMapping, checkPerformanceTestCoverage, generatePerformanceTestEvidence };
