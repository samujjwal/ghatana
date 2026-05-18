#!/usr/bin/env node
/**
 * check-duplication-exceptions.mjs
 *
 * Governance wrapper for duplication exception registry validation.
 * Delegates to validate-duplication-exceptions.mjs to verify:
 *   - All duplication exceptions are properly documented
 *   - Schema is valid
 *   - No expired exceptions remain
 *
 * Per kernel-todo.md §1.8 requirements.
 * Exits non-zero if any check fails.
 */

import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');

/**
 * Run duplication exception checks.
 * Returns an array of issue strings.
 */
export function runDuplicationExceptionChecks() {
  const issues = [];

  const validateScript = path.join(repoRoot, 'scripts', 'validate-duplication-exceptions.mjs');
  if (!existsSync(validateScript)) {
    issues.push('MISSING: scripts/validate-duplication-exceptions.mjs does not exist.');
    return issues;
  }

  try {
    execFileSync(process.execPath, [validateScript], {
      stdio: 'pipe',
      encoding: 'utf8',
    });
  } catch (err) {
    const message = (err.stderr || err.stdout || String(err)).trim();
    issues.push(`Duplication exception validation failed:\n  ${message}`);
  }

  return issues;
}

// CLI entrypoint
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const validateScript = path.join(repoRoot, 'scripts', 'validate-duplication-exceptions.mjs');

  let exitCode = 0;

  try {
    execFileSync(process.execPath, [validateScript], { stdio: 'inherit' });
  } catch {
    exitCode = 1;
  }

  if (exitCode === 0) {
    console.log('OK: duplication exception checks passed.');
  }

  process.exit(exitCode);
}
