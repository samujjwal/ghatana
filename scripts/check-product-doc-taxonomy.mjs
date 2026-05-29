#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const registryPath = path.join(repoRoot, 'config', 'documentation-surface-registry.json');

function loadRegistry() {
  if (!existsSync(registryPath)) {
    throw new Error('Missing config/documentation-surface-registry.json');
  }
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function checkProduct(productId, taxonomy) {
  const failures = [];
  const docsDir = path.join(repoRoot, 'products', productId, 'docs');

  if (!existsSync(docsDir)) {
    failures.push(`${productId}: missing docs directory`);
    return failures;
  }

  for (const docName of taxonomy.requiredForActive ?? []) {
    const requiredPath = path.join(docsDir, docName);
    if (!existsSync(requiredPath)) {
      if (docName === '03-API_CONTRACTS.md' && existsSync(path.join(docsDir, '02-API_CONTRACTS.md'))) {
        continue;
      }
      failures.push(`${productId}: missing canonical taxonomy document products/${productId}/docs/${docName}`);
    }
  }

  const designCandidates = [
    ...(taxonomy.designAlternates ?? []),
    ...(taxonomy.legacyCompat ?? []),
  ];
  const hasDesign = designCandidates.some((docName) => existsSync(path.join(docsDir, docName)));
  if (!hasDesign) {
    failures.push(`${productId}: missing design/UX document (expected one of: ${designCandidates.join(', ')})`);
  }

  return failures;
}

export function runProductDocTaxonomyCheck() {
  const registry = loadRegistry();
  const taxonomy = registry.taxonomy ?? {};
  const products = taxonomy.activeProducts ?? [];

  const violations = [];
  for (const productId of products) {
    violations.push(...checkProduct(productId, taxonomy));
  }

  return {
    passed: violations.length === 0,
    violations,
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = runProductDocTaxonomyCheck();

  if (result.passed) {
    console.log('Product documentation taxonomy validation passed.');
    process.exit(0);
  }

  console.error('Product documentation taxonomy violations:');
  for (const violation of result.violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}
