#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { loadCanonicalRegistry } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const configPath = path.join(repoRoot, 'config/product-cost-budgets.json');
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-cost-budgets.json');

function isPositiveNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0;
}

export function runProductCostBudgetCheck() {
  const violations = [];

  if (!existsSync(configPath)) {
    violations.push('Missing config/product-cost-budgets.json');
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
      violations.push(`Missing cost budget for active product ${productId}`);
      continue;
    }

    if (!isPositiveNumber(productBudget?.ai?.maxMonthlyUsd)) {
      violations.push(`Product ${productId} must define ai.maxMonthlyUsd > 0`);
    }
    if (!isPositiveNumber(productBudget?.ai?.maxTokensMonthly)) {
      violations.push(`Product ${productId} must define ai.maxTokensMonthly > 0`);
    }
    if (!isPositiveNumber(productBudget?.query?.maxMonthlyUsd)) {
      violations.push(`Product ${productId} must define query.maxMonthlyUsd > 0`);
    }
    if (!isPositiveNumber(productBudget?.export?.maxMonthlyUsd)) {
      violations.push(`Product ${productId} must define export.maxMonthlyUsd > 0`);
    }
    if (!isPositiveNumber(productBudget?.stream?.maxMonthlyUsd)) {
      violations.push(`Product ${productId} must define stream.maxMonthlyUsd > 0`);
    }
    if (!isPositiveNumber(productBudget?.storageGrowthGbPerMonth?.max)) {
      violations.push(`Product ${productId} must define storageGrowthGbPerMonth.max > 0`);
    }
    if (!isPositiveNumber(productBudget?.backgroundComputeHours?.max)) {
      violations.push(`Product ${productId} must define backgroundComputeHours.max > 0`);
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
  const result = runProductCostBudgetCheck();
  if (!result.pass) {
    console.error('Product cost budget check failed:');
    for (const violation of result.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('Product cost budget check passed.');
}

main();
