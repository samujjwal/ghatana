#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

const checks = [
  'check-product-lifecycle-profiles-schema.mjs',
  'check-toolchain-adapter-registry-schema.mjs',
  'check-product-lifecycle-contracts.mjs',
  'check-toolchain-adapter-contracts.mjs',
  'check-product-artifact-contracts.mjs',
  'check-product-deployment-contracts.mjs',
  'check-product-environment-contracts.mjs',
  'check-kernel-lifecycle-exclusions.mjs',
  'check-lifecycle-registry-config-drift.mjs',
  'check-digital-marketing-lifecycle-pilot.mjs',
  'check-kernel-plugin-registry.mjs',
  'check-product-shape-capability-matrix.mjs',
  'check-kernel-lifecycle-truth.mjs',
  'check-kernel-provider-mode.mjs',
  'check-agentic-lifecycle-action-contracts.mjs',
  'check-yappc-artifact-intelligence-boundary.mjs',
  'check-kernel-yappc-boundary.mjs',
  'check-kernel-product-unit-provider-contracts.mjs',
  // Task 1.4: Boundary, duplication, and package governance
  'check-domain-boundaries.mjs',
  'check-deprecated-imports.mjs',
  'check-package-registry.mjs',
  'check-duplicate-platform-capabilities.mjs',
];

function main() {
  for (const check of checks) {
    console.log(`Running ${check}...`);
    execFileSync(process.execPath, [join(repoRoot, 'scripts', check)], {
      cwd: repoRoot,
      stdio: 'inherit',
    });
  }

  console.log('Kernel platform lifecycle checks passed');
}

try {
  main();
} catch (error) {
  console.error(`Kernel platform lifecycle checks failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
