#!/usr/bin/env node

import { existsSync, mkdirSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { loadCanonicalRegistry } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-domain-invariants.json');

function collectFiles(rootDir, matcher) {
  const results = [];

  function walk(current) {
    if (!existsSync(current)) {
      return;
    }
    for (const entry of readdirSync(current)) {
      const fullPath = path.join(current, entry);
      const stats = statSync(fullPath);
      if (stats.isDirectory()) {
        if (entry === 'node_modules' || entry === '.git' || entry === 'build' || entry === 'dist') {
          continue;
        }
        walk(fullPath);
      } else if (matcher(entry, fullPath)) {
        results.push(fullPath);
      }
    }
  }

  walk(rootDir);
  return results;
}

export function runProductDomainInvariantCheck() {
  const registry = loadCanonicalRegistry(repoRoot);
  const activeBusinessProducts = Object.entries(registry)
    .filter(([, product]) => product.kind === 'business-product' && product.metadata?.status === 'active')
    .map(([productId]) => productId)
    .sort();

  const violations = [];
  const coverage = [];

  for (const productId of activeBusinessProducts) {
    const productRoot = path.join(repoRoot, 'products', productId);
    if (!existsSync(productRoot)) {
      violations.push(`Missing product directory for ${productId}`);
      continue;
    }

    const javaTests = collectFiles(productRoot, (name) => name.endsWith('Test.java') || name.endsWith('IT.java'));
    const tsTests = collectFiles(productRoot, (name) => /\.(test|spec)\.(ts|tsx|js|jsx)$/.test(name));
    const invariantNamed = [...javaTests, ...tsTests].filter((file) => /invariant|domain|workflow|lifecycle/i.test(file));

    coverage.push({
      productId,
      javaTestCount: javaTests.length,
      tsTestCount: tsTests.length,
      invariantNamedTestCount: invariantNamed.length,
    });

    if (javaTests.length + tsTests.length === 0) {
      violations.push(`Product ${productId} has no executable tests to prove domain invariants`);
      continue;
    }

    if (invariantNamed.length === 0) {
      violations.push(`Product ${productId} has tests but no domain-invariant/workflow/lifecycle-oriented test coverage`);
    }
  }

  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(
    evidencePath,
    `${JSON.stringify({
      generatedAt: new Date().toISOString(),
      status: violations.length === 0 ? 'passed' : 'failed',
      coverage,
      violations,
    }, null, 2)}\n`,
    'utf8',
  );

  return {
    pass: violations.length === 0,
    violations,
    coverage,
  };
}

function main() {
  const result = runProductDomainInvariantCheck();
  if (!result.pass) {
    console.error('Product domain invariant check failed:');
    for (const violation of result.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('Product domain invariant check passed.');
}

main();
