#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('..', import.meta.url).pathname);
const allowlist = JSON.parse(readFileSync(resolve(repoRoot, 'config/frontend-conformance-allowlist.json'), 'utf8'));
const files = execSync(
  "rg --files products/phr/apps/web/src products/digital-marketing/ui/src products/flashit/client/web/src products/flashit/client/mobile --glob '*.{tsx,css}'",
  { cwd: repoRoot, encoding: 'utf8' }
)
  .trim()
  .split('\n')
  .filter(Boolean);

const hardcodedValuePattern = /#[0-9a-fA-F]{3,8}\b|rgba?\(|style=\{\{/;
const violations = [];

for (const file of files) {
  if (allowlist.hardcodedStyleFiles.includes(file)) {
    continue;
  }
  const content = readFileSync(resolve(repoRoot, file), 'utf8');
  if (hardcodedValuePattern.test(content)) {
    violations.push(`${file}: hardcoded style value detected; prefer tokens or shared primitives`);
  }
}

if (violations.length > 0) {
  console.error('Design-system conformance violations:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Design-system conformance validation passed.');
