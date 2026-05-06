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
  '{ name: phr-web, workspaceDir: ., auditCommand: "pnpm --filter @ghatana/phr-web audit --audit-level=moderate --json > npm-audit.json || true" }',
  '{ name: dmos-ui, workspaceDir: ., auditCommand: "pnpm --filter @dmos/ui audit --audit-level=moderate --json > npm-audit.json || true" }',
  '{ name: flashit-web, workspaceDir: ., auditCommand: "pnpm --filter @flashit/web audit --audit-level=moderate --json > npm-audit.json || true" }',
  '{ name: flashit-gateway, workspaceDir: products/flashit, auditCommand: "pnpm --filter @flashit/web-api audit --audit-level=moderate --json > npm-audit.json || true" }',
  'run: bash scripts/security-audit.sh',
];

const violations = [];

for (const token of requiredTokens) {
  if (!source.includes(token)) {
    violations.push(`.github/workflows/security-scan.yml: missing required token ${JSON.stringify(token)}`);
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
