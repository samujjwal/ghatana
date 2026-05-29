#!/usr/bin/env node

import { spawnSync } from 'node:child_process';

const baseEnv = {
  ...process.env,
  DATACLOUD_RELEASE_GATE_BOOTSTRAP: 'product-release-readiness',
};

const passthroughArgs = process.argv.slice(2);
const requestedFull = passthroughArgs.includes('--full') || passthroughArgs.includes('--mode=full');
const requestedIteration = passthroughArgs.includes('--iteration') || passthroughArgs.includes('--mode=iteration');
const releaseReadinessMode = process.env.RELEASE_READINESS_MODE
  ?? (requestedFull ? 'full' : requestedIteration ? 'iteration' : 'iteration');

const checkScriptArgs = passthroughArgs.filter((arg) => arg !== '--iteration' && arg !== '--mode=iteration');

const commands = releaseReadinessMode === 'full'
  ? [
    ['pnpm', ['check:data-cloud-platform-provider-readiness']],
    ['pnpm', ['check:data-cloud-release-runtime-profile']],
    ['pnpm', ['check:wave2-product-quality-scorecard']],
    ['node', ['--test', './scripts/__tests__/check-product-release-readiness.test.mjs', './scripts/__tests__/product-registry-helper.test.mjs']],
    ['node', ['./scripts/check-product-release-readiness.mjs', ...checkScriptArgs]],
  ]
  : [
    ['node', ['./scripts/check-product-release-readiness.mjs', ...checkScriptArgs]],
  ];

if (releaseReadinessMode !== 'full') {
  console.log('ℹ️  Running scoped iteration release-readiness checks (set RELEASE_READINESS_MODE=full for full gate chain).');
}

for (const [command, args] of commands) {
  const result = spawnSync(command, args, {
    stdio: 'inherit',
    env: baseEnv,
    shell: process.platform === 'win32',
  });

  if ((result.status ?? 1) !== 0) {
    process.exit(result.status ?? 1);
  }
}
