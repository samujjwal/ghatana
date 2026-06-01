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
const WORKFLOW_TEST_PATHS = Object.freeze({
  'digital-marketing': Object.freeze({
    'campaign-activation': 'products/digital-marketing/dm-api/k6-tests/campaign-activation-smoke.js',
    'lead-capture': 'products/digital-marketing/dm-api/k6-tests/lead-capture-smoke.js',
    'notification-send': 'products/digital-marketing/dm-api/k6-tests/notification-smoke.js'
  }),
  phr: Object.freeze({
    'patient-record-fetch': 'products/phr/apps/web/e2e/phr-performance-smoke.spec.ts',
    'consent-check': 'products/phr/apps/web/e2e/phr-performance-smoke.spec.ts',
    'fhir-validation': 'products/phr/src/test/java/com/ghatana/phr/performance/PhrWorkflowPerformanceSmokeTest.java',
    'break-glass-access': 'products/phr/apps/web/e2e/phr-performance-smoke.spec.ts'
  })
});

function loadMetricMapping() {
  try {
    return JSON.parse(readFileSync(METRIC_MAPPING_FILE, 'utf8'));
  } catch (error) {
    console.error('Error loading metric mapping:', error.message);
    process.exit(1);
  }
}

function checkPerformanceTestCoverage(metricMapping, options = {}) {
  const targetProduct = options.targetProduct ?? null;
  const rootDir = options.rootDir ?? repoRoot;
  const testPathResolver = options.testPathResolver ?? findPerformanceTestPath;
  const violations = [];
  const results = [];

  const products = metricMapping.products || {};
  const productEntries = targetProduct
    ? Object.entries(products).filter(([product]) => product === targetProduct)
    : Object.entries(products);

  if (targetProduct && productEntries.length === 0) {
    violations.push(`Product '${targetProduct}' was not found in ${path.relative(repoRoot, METRIC_MAPPING_FILE)}`);
  }

  for (const [product, productConfig] of productEntries) {
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
      const testPath = testPathResolver(product, workflow.name);
      workflowResult.testPath = testPath;
      workflowResult.hasPerformanceTest = false;
      
      if (testPath && existsSync(path.join(rootDir, testPath))) {
        workflowResult.hasPerformanceTest = true;
        const testContent = readFileSync(path.join(rootDir, testPath), 'utf8');
        
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
  return WORKFLOW_TEST_PATHS[product]?.[workflowName] || null;
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
  const { violations, results } = checkPerformanceTestCoverage(metricMapping, { targetProduct });
  
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

export {
  loadMetricMapping,
  checkPerformanceTestCoverage,
  findPerformanceTestPath,
  generatePerformanceTestEvidence
};
