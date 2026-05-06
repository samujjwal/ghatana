#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const packagePaths = [
  'products/flashit/package.json',
  'products/flashit/client/web/package.json',
  'products/flashit/client/mobile/package.json',
  'products/flashit/backend/gateway/package.json',
  'products/flashit/libs/ts/shared/package.json',
];
const shellScriptPaths = [
  'products/flashit/backend/gateway/install-deps.sh',
  'products/flashit/backend/gateway/gen.sh',
  'products/flashit/backend/gateway/generate-prisma.sh',
  'products/flashit/backend/gateway/prisma-generate.sh',
  'products/flashit/backend/gateway/simple-generate.sh',
  'products/flashit/backend/gateway/rebuild-prisma.sh',
  'products/flashit/backend/gateway/setup.sh',
  'products/flashit/backend/gateway/verify-setup.sh',
  'products/flashit/backend/gateway/full-prisma-fix.sh',
];
const helperFilePaths = [
  'products/flashit/backend/gateway/generate-prisma-client.mjs',
  'products/flashit/backend/gateway/test-import.mjs',
  'products/flashit/backend/gateway/test-setup.mjs',
];

const violations = [];

for (const packagePath of packagePaths) {
  const pkg = JSON.parse(readFileSync(path.join(repoRoot, packagePath), 'utf8'));

  if (pkg.packageManager !== 'pnpm@10.33.0') {
    violations.push(`${packagePath}: packageManager must be pnpm@10.33.0`);
  }

  for (const [scriptName, scriptCommand] of Object.entries(pkg.scripts ?? {})) {
    if (typeof scriptCommand !== 'string') {
      continue;
    }
    if (/(^|[^a-z])npm run /.test(scriptCommand)) {
      violations.push(`${packagePath}: script "${scriptName}" must use pnpm run instead of npm run`);
    }
    if (/\bnpx\b/.test(scriptCommand)) {
      violations.push(`${packagePath}: script "${scriptName}" must use pnpm exec/dlx instead of npx`);
    }
    if (/\bpnpm install\b/.test(scriptCommand)) {
      violations.push(`${packagePath}: script "${scriptName}" must not perform nested pnpm install operations`);
    }
    if (/\bapps\/web-api\b|\bapps\/web\b|\bapps\/mobile\b/.test(scriptCommand)) {
      violations.push(`${packagePath}: script "${scriptName}" references stale FlashIt app paths`);
    }
  }
}

for (const scriptPath of shellScriptPaths) {
  const scriptSource = readFileSync(path.join(repoRoot, scriptPath), 'utf8');
  if (/\bnpm (install|ci|run)\b/.test(scriptSource)) {
    violations.push(`${scriptPath}: shell helper must not use npm install/ci/run`);
  }
  if (/\bnpx\b/.test(scriptSource)) {
    violations.push(`${scriptPath}: shell helper must use pnpm exec/dlx instead of npx`);
  }
  if (/\bpackage-lock\.json\b/.test(scriptSource)) {
    violations.push(`${scriptPath}: shell helper must not rely on package-lock.json`);
  }
}

for (const helperPath of helperFilePaths) {
  const helperSource = readFileSync(path.join(repoRoot, helperPath), 'utf8');
  if (/\bnpx\b/.test(helperSource)) {
    violations.push(`${helperPath}: helper must use pnpm exec/dlx instead of npx`);
  }
  if (/\bnpm (install|ci|run)\b/.test(helperSource)) {
    violations.push(`${helperPath}: helper must not reference npm install/ci/run commands`);
  }
}

const gatewayPackageLockPath = path.join(repoRoot, 'products/flashit/backend/gateway/package-lock.json');
try {
  readFileSync(gatewayPackageLockPath, 'utf8');
  violations.push('products/flashit/backend/gateway/package-lock.json: package-lock.json is not allowed in the FlashIt pnpm workspace');
} catch {
  // Expected: no package-lock in a pnpm-managed workspace package.
}

if (violations.length > 0) {
  console.error('FlashIt package manager check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('FlashIt package manager check passed.');
