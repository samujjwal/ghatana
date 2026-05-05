#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('..', import.meta.url).pathname);
const requiredDocs = [
  '00-VISION.md',
  '01-ARCHITECTURE.md',
  '02-API_CONTRACTS.md',
  '03-UX_WORKFLOWS.md',
  '04-TESTING.md',
  '05-OPERATIONS.md',
  '06-IMPLEMENTATION_PLAN.md',
];

const productDocs = {
  phr: 'products/phr/docs',
  finance: 'products/finance/docs',
  'digital-marketing': 'products/digital-marketing/docs',
  flashit: 'products/flashit/docs',
};

const rootDigitalMarketingDocs = [
  'digital-marketing-product-architecture.md',
  'digital-marketing-product-architecture-canonical.md',
  'digital-marketing-product-architecture-v2.md',
];

const violations = [];

for (const [product, baseDir] of Object.entries(productDocs)) {
  for (const docName of requiredDocs) {
    const docPath = resolve(repoRoot, baseDir, docName);
    if (!existsSync(docPath)) {
      violations.push(`${product}: missing canonical taxonomy document ${baseDir}/${docName}`);
    }
  }
}

for (const docName of rootDigitalMarketingDocs) {
  const docPath = resolve(repoRoot, docName);
  if (!existsSync(docPath)) {
    continue;
  }
  const content = readFileSync(docPath, 'utf8');
  if (!content.includes('products/digital-marketing/docs')) {
    violations.push(`${docName}: root-level DMOS docs must be replaced by a canonical link into products/digital-marketing/docs`);
  }
}

if (violations.length > 0) {
  console.error('Product documentation taxonomy violations:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Product documentation taxonomy validation passed.');
