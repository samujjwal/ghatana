#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');

const checks = [
  {
    name: 'TypeScript Kernel data-access contract tests',
    command: 'pnpm',
    args: [
      '--dir',
      'platform/typescript/data-access-context',
      'exec',
      'vitest',
      'run',
      'src/__tests__/data-access-context.test.ts',
    ],
  },
  {
    name: 'TypeScript product data-access conformance tests',
    command: 'pnpm',
    args: [
      '--dir',
      'platform/typescript/product-conformance',
      'exec',
      'vitest',
      'run',
      'src/data-access/__tests__/data-access.test.ts',
      'src/suite/__tests__/active-products.test.ts',
    ],
  },
  {
    name: 'Java Kernel data-access context tests',
    command: './gradlew',
    args: [
      ':platform:java:database:test',
      '--tests',
      'com.ghatana.platform.database.DataAccessContextTest',
      '--tests',
      'com.ghatana.platform.database.MutationMetadataEnricherTest',
      '--no-daemon',
    ],
  },
];

const failures = [];

for (const check of checks) {
  console.log(`\n=== ${check.name} ===`);
  const result = spawnSync(check.command, check.args, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });

  if (result.error) {
    failures.push(`${check.name}: ${result.error.message}`);
    continue;
  }

  if (result.status !== 0) {
    failures.push(`${check.name}: exited with status ${result.status}`);
  }
}

if (failures.length > 0) {
  console.error('\nData-access contract check failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('\nData-access contract check passed (typed TS conformance + Java contract tests).');
