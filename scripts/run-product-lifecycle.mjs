#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

function usage(exitCode = 1) {
  const stream = exitCode === 0 ? process.stdout : process.stderr;
  stream.write('Usage: node scripts/run-product-lifecycle.mjs <productId> <phase> [options]\n');
  process.exit(exitCode);
}

function main() {
  const args = process.argv.slice(2);
  if (args.includes('--help') || args.length < 2) {
    usage(args.includes('--help') ? 0 : 1);
  }

  const [productId, phase, ...rest] = args;
  const result = spawnSync(process.execPath, [join(repoRoot, 'scripts', 'kernel-product.mjs'), 'product', phase, productId, ...rest], {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: false,
  });

  if (result.error) {
    throw result.error;
  }
  process.exit(result.status ?? 1);
}

try {
  main();
} catch (error) {
  console.error(`run-product-lifecycle failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
