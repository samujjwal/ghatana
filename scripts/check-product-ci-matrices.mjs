#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const checks = [
  {
    file: '.github/workflows/product-coverage-gates.yml',
    label: 'product coverage gates',
    requiredTokens: [
      "      - 'products/finance/**'",
      "      - 'products/phr/**'",
      "      - 'products/digital-marketing/**'",
      "      - 'products/flashit/**'",
      '- product: Finance',
      '- product: PHR',
      '- product: Digital Marketing',
      '- product: FlashIt',
      "taskPrefix: ':products:finance'",
      "taskPrefix: ':products:phr'",
      "taskPrefix: ':products:digital-marketing:dm-domain-packs'",
      "taskPrefix: ':products:flashit'",
    ],
  },
  {
    file: '.github/workflows/api-contract-conformance.yml',
    label: 'API contract conformance',
    requiredTokens: [
      "      - 'products/phr/**'",
      "      - 'products/finance/**'",
      "      - 'products/digital-marketing/**'",
      "      - 'products/flashit/**'",
      '- product: PHR',
      '- product: Finance',
      '- product: Digital Marketing',
      '- product: FlashIt',
      './gradlew :products:phr:checkApiContractConformance --no-daemon --stacktrace',
      './gradlew :products:finance:checkApiContractConformance --no-daemon --stacktrace',
      './gradlew :products:digital-marketing:dm-api:test --tests "*ContractTest" --no-daemon --stacktrace',
      'pnpm --filter @ghatana/flashit-tests test:contract',
      'pnpm@10.33.0',
    ],
  },
];

const violations = [];

for (const check of checks) {
  const workflowPath = path.join(repoRoot, check.file);
  const source = readFileSync(workflowPath, 'utf8');

  for (const token of check.requiredTokens) {
    if (!source.includes(token)) {
      violations.push(`${check.file}: ${check.label} is missing required token ${JSON.stringify(token)}`);
    }
  }
}

if (violations.length > 0) {
  console.error('Product CI matrix check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Product CI matrix check passed.');
