#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const workflowPath = path.join(repoRoot, '.github/workflows/security-scan.yml');
const source = readFileSync(workflowPath, 'utf8');

const requiredTokens = [
  'name: Node Dependency Vulnerability Scan',
  'uses: pnpm/action-setup@v4',
  'version: 10.33.0',
  'run: bash scripts/security-audit.sh',
];

const requiredMatrixEntries = [
  { name: 'phr-web', workspaceDir: '.', filter: '--filter @ghatana/phr-web' },
  { name: 'dmos-ui', workspaceDir: '.', filter: '--filter @dmos/ui' },
  { name: 'flashit-web', workspaceDir: '.', filter: '--filter @flashit/web' },
  { name: 'flashit-gateway', workspaceDir: 'products/flashit', filter: '--filter @flashit/web-api' },
];

const violations = [];

for (const token of requiredTokens) {
  if (!source.includes(token)) {
    violations.push(`.github/workflows/security-scan.yml: missing required token ${JSON.stringify(token)}`);
  }
}

for (const entry of requiredMatrixEntries) {
  const matrixLine = `- { name: ${entry.name}, workspaceDir: ${entry.workspaceDir}, auditFilter: "${entry.filter}" }`;
  const legacyMatrixLine = `- { name: ${entry.name}, workspaceDir: ${entry.workspaceDir}, auditCommand: "pnpm ${entry.filter} audit --audit-level=moderate --json > npm-audit.json || true" }`;
  if (!source.includes(matrixLine) && !source.includes(legacyMatrixLine)) {
    violations.push(
      `.github/workflows/security-scan.yml: missing required matrix entry for ${entry.name} with filter ${entry.filter}`,
    );
  }
}

if (violations.length > 0) {
  console.error('Security workflow coverage check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Security workflow coverage check passed.');
