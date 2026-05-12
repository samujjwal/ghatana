#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const workspacePath = path.join(repoRoot, 'pnpm-workspace.yaml');
const shapePath = path.join(repoRoot, 'config/product-shape.json');
const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
const financeReadmePath = path.join(repoRoot, 'products/finance/README.md');

const workspaceSource = readFileSync(workspacePath, 'utf8');
const shape = JSON.parse(readFileSync(shapePath, 'utf8'));
const registry = JSON.parse(readFileSync(registryPath, 'utf8'));
const financeReadme = readFileSync(financeReadmePath, 'utf8');

const violations = [];

for (const product of Object.values(registry.registry)) {
  for (const packagePattern of product.pnpmPackages ?? []) {
    const token = `- "${packagePattern}"`;
    if (!workspaceSource.includes(token)) {
      violations.push(`pnpm-workspace.yaml: missing registry workspace registration ${JSON.stringify(token)}`);
    }
  }
}

if (shape.products.finance?.ui !== false || shape.products.finance?.uiMode !== 'backend-only') {
  violations.push('config/product-shape.json: finance must be declared as ui=false and uiMode="backend-only"');
}

if (!financeReadme.toLowerCase().includes('backend-only')) {
  violations.push('products/finance/README.md: missing backend-only declaration');
}

if (violations.length > 0) {
  console.error('Product workspace registration check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Product workspace registration check passed.');
