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
    name: 'Kernel core compile baseline for backend route tests',
    command: process.execPath,
    args: [
      './scripts/run-gradle-wrapper.mjs',
      ':platform-kernel:kernel-core:compileJava',
      '--no-daemon',
      '--max-workers=1',
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
    retries: 1,
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

  let attempt = 0;
  const maxAttempts = 1 + (check.retries ?? 0);
  let finalResult = null;

  while (attempt < maxAttempts) {
    attempt += 1;

    if (attempt > 1) {
      console.warn(`Retrying ${check.name} (attempt ${attempt}/${maxAttempts}) after non-zero exit`);
    }

    const result = spawnSync(check.command, check.args, {
      cwd: repoRoot,
      stdio: 'inherit',
      shell: check.shell ?? false,
    });

    finalResult = result;
    if (!result.error && result.status === 0) {
      break;
    }
  }

  if (finalResult?.error) {
    failures.push(`${check.name}: ${finalResult.error.message}`);
    continue;
  }

  if (finalResult?.status !== 0) {
    failures.push(`${check.name}: exited with status ${finalResult?.status}`);
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
