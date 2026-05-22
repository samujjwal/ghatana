#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { loadCanonicalRegistry } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const configPath = path.join(repoRoot, 'config/product-slo-budgets.json');
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-slo-budgets.json');

function isPositiveNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0;
}

export function runProductSloBudgetCheck() {
  const violations = [];

  if (!existsSync(configPath)) {
    violations.push('Missing config/product-slo-budgets.json');
  }

  const registry = loadCanonicalRegistry(repoRoot);
  const activeBusinessProducts = Object.entries(registry)
    .filter(([, product]) => product.kind === 'business-product' && product.metadata?.status === 'active')
    .map(([productId]) => productId)
    .sort();

  const config = existsSync(configPath)
    ? JSON.parse(readFileSync(configPath, 'utf8'))
    : { products: {} };

  for (const productId of activeBusinessProducts) {
    const productBudget = config.products?.[productId];
    if (!productBudget) {
      violations.push(`Missing SLO budget for active product ${productId}`);
      continue;
    }

    const workflows = productBudget.workflows;
    if (!workflows || typeof workflows !== 'object' || Object.keys(workflows).length === 0) {
      violations.push(`Product ${productId} must define at least one SLO workflow budget`);
      continue;
    }

    for (const [workflowId, workflowBudget] of Object.entries(workflows)) {
      const latency = workflowBudget?.latencyMs;
      if (!latency || !isPositiveNumber(latency.p50) || !isPositiveNumber(latency.p95) || !isPositiveNumber(latency.p99)) {
        violations.push(`Product ${productId}/${workflowId} must define positive latency p50/p95/p99`);
      } else if (!(latency.p50 <= latency.p95 && latency.p95 <= latency.p99)) {
        violations.push(`Product ${productId}/${workflowId} must satisfy latency ordering p50 <= p95 <= p99`);
      }

      if (!isPositiveNumber(workflowBudget?.throughputRps?.min)) {
        violations.push(`Product ${productId}/${workflowId} must define throughputRps.min > 0`);
      }
      if (!isPositiveNumber(workflowBudget?.memoryMb?.max)) {
        violations.push(`Product ${productId}/${workflowId} must define memoryMb.max > 0`);
      }
      if (!isPositiveNumber(workflowBudget?.queueDepth?.max)) {
        violations.push(`Product ${productId}/${workflowId} must define queueDepth.max > 0`);
      }
      if (!isPositiveNumber(workflowBudget?.backgroundJobRuntimeMs?.max)) {
        violations.push(`Product ${productId}/${workflowId} must define backgroundJobRuntimeMs.max > 0`);
      }
    }
  }

  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(
    evidencePath,
    `${JSON.stringify({
      generatedAt: new Date().toISOString(),
      status: violations.length === 0 ? 'passed' : 'failed',
      activeBusinessProducts,
      violations,
    }, null, 2)}\n`,
    'utf8',
  );

  return {
    pass: violations.length === 0,
    violations,
  };
}

function main() {
  const result = runProductSloBudgetCheck();
  if (!result.pass) {
    console.error('Product SLO budget check failed:');
    for (const violation of result.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('Product SLO budget check passed.');
}

main();
