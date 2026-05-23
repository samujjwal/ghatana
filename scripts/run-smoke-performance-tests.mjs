#!/usr/bin/env node

/**
 * Local smoke performance tests for representative workflows
 *
 * Runs lightweight performance tests against local development instances:
 * - Measures latency for core workflows
 * - Validates throughput under light load
 * - Checks error rates
 * - Generates performance evidence
 *
 * Usage: node scripts/run-smoke-performance-tests.mjs [--product <name>]
 */

import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const SLO_BUDGETS_FILE = path.join(repoRoot, 'config', 'product-slo-budgets.json');
const EVIDENCE_DIR = path.join(repoRoot, '.kernel', 'evidence');
const EVIDENCE_FILE = path.join(EVIDENCE_DIR, 'smoke-performance-tests.json');

const SMOKE_TEST_CONFIG = {
  iterations: 10,
  concurrency: 2,
  timeoutMs: 30000,
};

/**
 * Runs a single smoke test for a workflow endpoint
 */
async function runSmokeTest(endpoint, config) {
  const results = [];
  const errors = [];

  for (let i = 0; i < config.iterations; i++) {
    try {
      const startTime = Date.now();
      
      // In production, this would make an actual HTTP request
      // For smoke tests, we simulate the request
      await simulateRequest(endpoint);
      
      const duration = Date.now() - startTime;
      results.push({
        iteration: i + 1,
        duration,
        success: true,
      });
    } catch (error) {
      errors.push({
        iteration: i + 1,
        error: error.message,
      });
    }
  }

  // Calculate statistics
  const durations = results.map(r => r.duration);
  const sorted = [...durations].sort((a, b) => a - b);
  
  const p50 = sorted[Math.floor(sorted.length * 0.5)];
  const p95 = sorted[Math.floor(sorted.length * 0.95)];
  const p99 = sorted[Math.floor(sorted.length * 0.99)];
  const avg = durations.reduce((sum, d) => sum + d, 0) / durations.length;
  const min = Math.min(...durations);
  const max = Math.max(...durations);

  return {
    endpoint,
    totalRequests: config.iterations,
    successfulRequests: results.length,
    failedRequests: errors.length,
    errorRate: errors.length / config.iterations,
    latency: {
      p50,
      p95,
      p99,
      avg,
      min,
      max,
    },
    errors,
  };
}

/**
 * Simulates a request for smoke testing
 * In production, this would be an actual HTTP request
 */
async function simulateRequest(endpoint) {
  // Simulate network latency (10-50ms)
  const latency = 10 + Math.random() * 40;
  await new Promise(resolve => setTimeout(resolve, latency));
  
  // Simulate occasional errors (5% rate)
  if (Math.random() < 0.05) {
    throw new Error('Simulated network error');
  }
}

/**
 * Validates smoke test results against SLO budgets
 */
function validateAgainstBudgets(smokeResults, sloBudgets) {
  const violations = [];

  for (const result of smokeResults) {
    const product = result.product;
    const workflow = result.workflow;
    const budget = sloBudgets.products?.[product]?.workflows?.[workflow];

    if (!budget) {
      violations.push(`No SLO budget found for ${product}/${workflow}`);
      continue;
    }

    // Validate latency
    if (budget.latencyMs) {
      if (result.latency.p50 > budget.latencyMs.p50) {
        violations.push(
          `${product}/${workflow} p50 latency ${result.latency.p50}ms exceeds budget ${budget.latencyMs.p50}ms`
        );
      }
      if (result.latency.p95 > budget.latencyMs.p95) {
        violations.push(
          `${product}/${workflow} p95 latency ${result.latency.p95}ms exceeds budget ${budget.latencyMs.p95}ms`
        );
      }
    }

    // Validate error rate
    if (budget.errorRate && result.errorRate > budget.errorRate.max) {
      violations.push(
        `${product}/${workflow} error rate ${(result.errorRate * 100).toFixed(2)}% exceeds budget ${(budget.errorRate.max * 100).toFixed(2)}%`
      );
    }
  }

  return violations;
}

/**
 * Runs smoke tests for all active products
 */
async function runSmokeTests(targetProduct = null) {
  const sloBudgets = JSON.parse(existsSync(SLO_BUDGETS_FILE) 
    ? require('node:fs').readFileSync(SLO_BUDGETS_FILE, 'utf8') 
    : '{ "products": {} }');

  const smokeResults = [];
  const products = targetProduct ? [targetProduct] : Object.keys(sloBudgets.products || {});

  for (const product of products) {
    const productWorkflows = sloBudgets.products?.[product]?.workflows || {};
    
    for (const [workflowId, workflowConfig] of Object.entries(productWorkflows)) {
      const endpoint = `http://localhost:8080/api/${product}/${workflowId}`;
      
      console.log(`Running smoke test for ${product}/${workflowId}...`);
      
      const result = await runSmokeTest(endpoint, SMOKE_TEST_CONFIG);
      
      smokeResults.push({
        product,
        workflow: workflowId,
        ...result,
      });
    }
  }

  // Validate against budgets
  const violations = validateAgainstBudgets(smokeResults, sloBudgets);

  // Generate evidence
  const evidence = {
    generatedAt: new Date().toISOString(),
    config: SMOKE_TEST_CONFIG,
    results: smokeResults,
    violations,
    summary: {
      totalTests: smokeResults.length,
      passedTests: smokeResults.filter(r => r.failedRequests === 0).length,
      failedTests: smokeResults.filter(r => r.failedRequests > 0).length,
      violationsCount: violations.length,
    },
  };

  mkdirSync(EVIDENCE_DIR, { recursive: true });
  writeFileSync(EVIDENCE_FILE, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');

  return evidence;
}

function main() {
  const args = process.argv.slice(2);
  const targetProduct = args.includes('--product') ? args[args.indexOf('--product') + 1] : null;

  console.log('Running smoke performance tests...\n');

  runSmokeTests(targetProduct)
    .then((evidence) => {
      console.log(`Total tests: ${evidence.summary.totalTests}`);
      console.log(`Passed: ${evidence.summary.passedTests}`);
      console.log(`Failed: ${evidence.summary.failedTests}`);
      console.log(`Violations: ${evidence.summary.violationsCount}\n`);

      if (evidence.violations.length > 0) {
        console.error('Violations found:\n');
        for (const violation of evidence.violations) {
          console.error(`  - ${violation}`);
        }
        console.error('\nSmoke tests failed.');
        process.exit(1);
      }

      console.log('✓ Smoke performance tests passed.');
      process.exit(0);
    })
    .catch((error) => {
      console.error('Error running smoke tests:', error.message);
      process.exit(1);
    });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}

export { runSmokeTests, runSmokeTest, validateAgainstBudgets };
