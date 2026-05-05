#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('..', import.meta.url).pathname);
const allowlist = JSON.parse(readFileSync(resolve(repoRoot, 'config/frontend-conformance-allowlist.json'), 'utf8'));
const files = execSync(
  "rg --files products --glob 'PageHeader.tsx' --glob 'PageLayout.tsx' --glob 'AppShell.tsx'",
  { cwd: repoRoot, encoding: 'utf8' }
)
  .trim()
  .split('\n')
  .filter(Boolean);

const violations = [];

for (const file of files) {
  if (allowlist.localLayoutPrimitiveFiles.includes(file)) {
    continue;
  }
  if (file.includes('platform/typescript') || file.includes('product-shell')) {
    continue;
  }
  const content = readFileSync(resolve(repoRoot, file), 'utf8');
  if (!content.includes('@ghatana/product-shell') && !content.includes('@ghatana/design-system')) {
    violations.push(`${file}: product-local layout primitive should compose shared shell/design-system components`);
  }
}

if (violations.length > 0) {
  console.error('Shared layout primitive violations:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Shared layout primitive validation passed.');
