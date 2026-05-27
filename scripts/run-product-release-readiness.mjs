#!/usr/bin/env node

import { spawnSync } from 'node:child_process';

const baseEnv = {
  ...process.env,
  DATACLOUD_RELEASE_GATE_BOOTSTRAP: 'product-release-readiness',
};

const commands = [
  ['pnpm', ['check:data-cloud-platform-provider-readiness']],
  ['pnpm', ['check:data-cloud-release-runtime-profile']],
  ['pnpm', ['check:wave2-product-quality-scorecard']],
  ['node', ['--test', './scripts/__tests__/check-product-release-readiness.test.mjs', './scripts/__tests__/product-registry-helper.test.mjs']],
  ['node', ['./scripts/check-product-release-readiness.mjs']],
];

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
