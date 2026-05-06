#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const checks = [
  {
    file: '.github/workflows/performance-budgets.yml',
    label: 'performance budgets workflow',
    requiredTokens: [
      "      - 'products/phr/apps/web/**'",
      "      - 'products/digital-marketing/ui/**'",
      "      - 'products/flashit/client/**'",
      'uses: pnpm/action-setup@v4',
      'version: 10.33.0',
      '- name: phr-web',
      'path: products/phr/apps/web',
      '- name: dmos-ui',
      'path: products/digital-marketing/ui',
      '- name: flashit-web',
      'path: products/flashit/client/web',
    ],
  },
  {
    file: '.github/workflows/lighthouse.yml',
    label: 'lighthouse workflow',
    requiredTokens: [
      "      - 'products/*/ui/**'",
      "      - 'products/*/client/web/**'",
      "      - 'products/*/apps/*/src/**'",
      'uses: pnpm/action-setup@v4',
      'version: 10.33.0',
    ],
  },
];

const violations = [];

for (const check of checks) {
  const absolutePath = path.join(repoRoot, check.file);
  const source = readFileSync(absolutePath, 'utf8');

  for (const token of check.requiredTokens) {
    if (!source.includes(token)) {
      violations.push(`${check.file}: ${check.label} is missing required token ${JSON.stringify(token)}`);
    }
  }
}

if (violations.length > 0) {
  console.error('Audited performance workflow check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Audited performance workflow check passed.');
