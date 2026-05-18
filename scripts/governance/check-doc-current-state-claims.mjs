#!/usr/bin/env node
/**
 * check-doc-current-state-claims.mjs
 *
 * Governance wrapper for documentation current-state claim validation.
 * Delegates to:
 *   - check-current-state-claims.mjs  — verifies that docs' "current state" claims match reality
 *   - check-doc-authority.mjs        — verifies documentation authority map
 *   - check-doc-truth.mjs            — verifies documentation truth surface
 *
 * Per kernel-todo.md §1.7 requirements.
 * Exits non-zero if any check fails.
 */

import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');

/** Scripts to run in order. Each must exit 0 for the check to pass. */
const DOC_CLAIM_SCRIPTS = [
  'scripts/check-current-state-claims.mjs',
  'scripts/check-doc-authority.mjs',
  'scripts/check-doc-truth.mjs',
];

/**
 * Run all documentation current-state claim checks.
 * Returns an array of failure objects { script, message }.
 */
export function runDocCurrentStateClaimChecks() {
  const failures = [];

  for (const scriptPath of DOC_CLAIM_SCRIPTS) {
    const absolutePath = path.join(repoRoot, scriptPath);
    try {
      execFileSync(process.execPath, [absolutePath], {
        stdio: 'pipe',
        encoding: 'utf8',
      });
    } catch (err) {
      const message = (err.stderr || err.stdout || String(err)).trim();
      failures.push({ script: scriptPath, message });
    }
  }

  return failures;
}

// CLI entrypoint
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  console.log('Running documentation current-state claim checks...');

  let exitCode = 0;

  for (const scriptPath of DOC_CLAIM_SCRIPTS) {
    const absolutePath = path.join(repoRoot, scriptPath);
    try {
      execFileSync(process.execPath, [absolutePath], { stdio: 'inherit' });
    } catch {
      exitCode = 1;
    }
  }

  if (exitCode === 0) {
    console.log('OK: all documentation current-state claim checks passed.');
  } else {
    console.error('FAIL: one or more documentation checks failed. See output above.');
  }

  process.exit(exitCode);
}
