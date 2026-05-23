#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const requirementsPath = path.join(root, 'config/product-release-evidence-requirements.json');
const evidenceRoot = path.join(root, '.kernel/evidence');

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function resolveProductScope(requirements) {
  const provided = process.env.AFFECTED_PRODUCTS;
  if (!provided || provided.trim().length === 0) {
    return Object.keys(requirements.products ?? {});
  }

  return provided
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .filter((productId) => Boolean(requirements.products?.[productId]));
}

function evaluateCategory(refs) {
  const missing = [];
  for (const ref of refs) {
    const relativePath = String(ref);
    if (!existsSync(path.join(root, relativePath))) {
      missing.push(relativePath);
    }
  }

  return {
    refs,
    missing,
    status: missing.length === 0 ? 'passed' : 'failed',
  };
}

function main() {
  if (!existsSync(requirementsPath)) {
    throw new Error('Missing config/product-release-evidence-requirements.json');
  }

  const requirements = readJson(requirementsPath);
  const products = resolveProductScope(requirements);
  const violations = [];
  const summaryRows = [];

  mkdirSync(evidenceRoot, { recursive: true });

  for (const productId of products) {
    const definition = requirements.products?.[productId];
    const categories = definition?.requiredCategories ?? {};
    const categoryResults = {};

    for (const [category, refs] of Object.entries(categories)) {
      categoryResults[category] = evaluateCategory(Array.isArray(refs) ? refs : []);
      if (categoryResults[category].status === 'failed') {
        violations.push(
          `${productId} category ${category} missing evidence: ${categoryResults[category].missing.join(', ')}`,
        );
      }
    }

    const packStatus = Object.values(categoryResults).every((result) => result.status === 'passed')
      ? 'passed'
      : 'failed';

    const pack = {
      generatedAt: new Date().toISOString(),
      productId,
      status: packStatus,
      categories: categoryResults,
    };

    const packPath = path.join(evidenceRoot, `product-release-evidence-pack.${productId}.json`);
    writeFileSync(packPath, `${JSON.stringify(pack, null, 2)}\n`, 'utf8');

    summaryRows.push({
      productId,
      status: packStatus,
      categoryCount: Object.keys(categoryResults).length,
    });
  }

  const summaryPath = path.join(evidenceRoot, 'product-release-evidence-packs.json');
  writeFileSync(
    summaryPath,
    `${JSON.stringify({
      generatedAt: new Date().toISOString(),
      status: violations.length === 0 ? 'passed' : 'failed',
      products: summaryRows,
      violations,
    }, null, 2)}\n`,
    'utf8',
  );

  if (violations.length > 0) {
    console.error('Product release evidence-pack check failed:\n');
    for (const violation of violations) {
      console.error(`- ${violation}`);
    }
    console.error(`\nEvidence written to ${path.relative(root, summaryPath)}`);
    process.exit(1);
  }

  console.log(`Product release evidence-pack check passed. Evidence: ${path.relative(root, summaryPath)}`);
}

main();
