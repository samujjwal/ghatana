#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const workflowPath = path.join(repoRoot, '.github/workflows/e2e-tests.yml');
const workflowSource = readFileSync(workflowPath, 'utf8');

const requiredTokens = [
  'name: E2E Tests',
  'uses: pnpm/action-setup@v4',
  'version: 10.33.0',
  'product: flashit-web',
  'path: products/flashit/client/web',
  'product: phr-web',
  'path: products/phr/apps/web',
  'product: dmos-ui',
  'path: products/digital-marketing/ui',
  'run: pnpm run test:e2e',
];

const violations = [];

for (const token of requiredTokens) {
  if (!workflowSource.includes(token)) {
    violations.push(`${workflowPath}: missing required token ${JSON.stringify(token)}`);
  }
}

if (violations.length > 0) {
  console.error('Audited E2E workflow check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Audited E2E workflow check passed.');
