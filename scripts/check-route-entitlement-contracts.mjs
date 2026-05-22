#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const pnpmCommand = 'pnpm';
const pnpmShell = process.platform === 'win32';

const checks = [
  {
    name: 'Kernel route-entitlement conformance package',
    command: pnpmCommand,
    shell: pnpmShell,
    args: [
      '--dir',
      'platform/typescript/product-conformance',
      'exec',
      'vitest',
      'run',
      'src/route-entitlements/__tests__/route-entitlements.test.ts',
      'src/suite/__tests__/suite.test.ts',
    ],
  },
  {
    name: 'PHR backend route-entitlement behavior',
    command: process.execPath,
    args: [
      './scripts/run-gradle-wrapper.mjs',
      ':products:phr:test',
      '--tests',
      'com.ghatana.phr.api.PhrHttpServerTest',
      '--no-daemon',
    ],
  },
  {
    name: 'DMOS backend route-entitlement behavior',
    command: process.execPath,
    args: [
      './scripts/run-gradle-wrapper.mjs',
      ':products:digital-marketing:dm-api:test',
      '--tests',
      'com.ghatana.digitalmarketing.api.DmosRouteEntitlementServletTest',
      '--no-daemon',
    ],
  },
  {
    name: 'FlashIt backend route-entitlement behavior',
    command: pnpmCommand,
    shell: pnpmShell,
    args: [
      '--dir',
      'products/flashit/backend/gateway',
      'exec',
      'vitest',
      'run',
      'src/routes/__tests__/entitlements.test.ts',
    ],
  },
];

const failures = [];

for (const check of checks) {
  console.log(`\n=== ${check.name} ===`);
  const result = spawnSync(check.command, check.args, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: check.shell ?? false,
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
  console.error('\nRoute entitlement contract check failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('\nRoute entitlement contract check passed (typed conformance + backend behavior tests).');
