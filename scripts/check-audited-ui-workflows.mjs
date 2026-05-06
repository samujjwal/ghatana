#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const approvedPnpmVersion = '10.33.0';

const checks = [
  {
    file: '.github/workflows/accessibility.yml',
    label: 'accessibility workflow',
    requiredTokens: [
      "name: Product Accessibility Conformance",
      "uses: pnpm/action-setup@v4",
      `version: ${approvedPnpmVersion}`,
      "package: '@ghatana/phr-web'",
      "package: '@dmos/ui'",
      "package: '@flashit/web'",
      "run: pnpm --filter \"${{ matrix.package }}\" run test:e2e:a11y",
    ],
  },
  {
    file: '.github/workflows/visual-regression.yml',
    label: 'visual regression workflow',
    requiredTokens: [
      "uses: pnpm/action-setup@v4",
      `version: ${approvedPnpmVersion}`,
      "product: flashit-web",
      "path: products/flashit/client/web",
      "product: phr-web",
      "path: products/phr/apps/web",
      "product: dmos-ui",
      "path: products/digital-marketing/ui",
      "--grep=\"@visual\"",
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
  console.error('Audited UI workflow check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Audited UI workflow check passed.');
