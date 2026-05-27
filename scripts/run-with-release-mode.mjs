#!/usr/bin/env node

import { spawnSync } from 'node:child_process';

const [, , scriptPath, ...args] = process.argv;

if (!scriptPath) {
  console.error('Usage: node ./scripts/run-with-release-mode.mjs <script> [args...]');
  process.exit(1);
}

const result = spawnSync(process.execPath, [scriptPath, ...args], {
  stdio: 'inherit',
  env: {
    ...process.env,
    RELEASE_MODE: 'release',
  },
});

process.exit(result.status ?? 1);
