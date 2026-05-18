#!/usr/bin/env node
/**
 * check-boundaries.mjs
 *
 * Governance orchestrator for architecture boundary checks.
 * Delegates to the three canonical boundary scripts:
 *   - check-domain-boundaries.mjs
 *   - check-kernel-boundaries.mjs
 *   - check-platform-product-boundaries.mjs
 *
 * Per kernel-todo.md §1.6 requirements.
 * Exits non-zero if any boundary check fails.
 */

import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');

const BOUNDARY_SCRIPTS = [
  'scripts/check-domain-boundaries.mjs',
  'scripts/check-kernel-boundaries.mjs',
  'scripts/check-platform-product-boundaries.mjs',
];

/**
 * Run all architecture boundary checks.
 * Returns an array of failure objects { script, exitCode }.
 */
export function runBoundaryChecks() {
  const failures = [];

  for (const scriptPath of BOUNDARY_SCRIPTS) {
    const absolutePath = path.join(repoRoot, scriptPath);
    try {
      execFileSync(process.execPath, [absolutePath], {
        stdio: 'pipe',
        encoding: 'utf8',
      });
    } catch (err) {
      const message = err.stderr || err.stdout || String(err);
      failures.push({ script: scriptPath, message: message.trim() });
    }
  }

  return failures;
}

// CLI entrypoint
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  console.log('Running architecture boundary checks...');

  let exitCode = 0;

  for (const scriptPath of BOUNDARY_SCRIPTS) {
    const absolutePath = path.join(repoRoot, scriptPath);
    try {
      execFileSync(process.execPath, [absolutePath], { stdio: 'inherit' });
    } catch {
      exitCode = 1;
    }
  }

  if (exitCode === 0) {
    console.log('OK: all architecture boundary checks passed.');
  } else {
    console.error('FAIL: one or more boundary checks failed. See output above.');
  }

  process.exit(exitCode);
}
