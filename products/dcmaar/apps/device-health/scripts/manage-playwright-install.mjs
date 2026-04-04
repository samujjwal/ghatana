#!/usr/bin/env node

import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectDir = dirname(scriptDir);
const cliPath = join(projectDir, 'node_modules', 'playwright', 'cli.js');
const browserArgs = ['install', 'chromium', 'firefox', 'msedge'];
const forceInstall = process.argv.includes('--force');
const shouldInstall = forceInstall || process.env.DCMAAR_INSTALL_PLAYWRIGHT === '1';

if (!shouldInstall) {
  console.log(
    'Skipping Playwright browser install during dependency install. Set DCMAAR_INSTALL_PLAYWRIGHT=1 to opt in, or run pnpm run setup:playwright when needed.'
  );
  process.exit(0);
}

if (!existsSync(cliPath)) {
  console.warn(
    'Playwright CLI is not available yet. Run pnpm run setup:playwright after dependencies finish installing.'
  );
  process.exit(0);
}

const result = spawnSync(process.execPath, [cliPath, ...browserArgs], {
  cwd: projectDir,
  stdio: 'inherit',
  env: process.env,
});

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

console.log('');
console.log('Browser installation complete.');
console.log('If the host later reports missing system libraries, run:');
console.log('  sudo npx playwright install-deps chromium firefox msedge');
