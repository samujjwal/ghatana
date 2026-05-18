#!/usr/bin/env node
/**
 * run-governance-checks.mjs
 *
 * Master governance orchestrator. Runs all governance checks in order and
 * reports a combined pass/fail result. Exits non-zero if any check fails.
 *
 * Usage:
 *   node scripts/governance/run-governance-checks.mjs
 *   pnpm check:governance
 *
 * Individual checks:
 *   check-domain-registry.mjs          — domain registry validation
 *   check-product-registry-consistency.mjs — product registry + lifecycle pilot
 *   check-package-governance.mjs       — TypeScript package governance
 *   check-boundaries.mjs               — architecture boundary enforcement
 *   check-doc-current-state-claims.mjs — documentation authority + truth
 *   check-duplication-exceptions.mjs   — duplication exception registry
 *
 * Per kernel-todo.md §1.9.
 */

import { execFileSync, spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');

/** All governance checks to run in order. */
const GOVERNANCE_CHECKS = [
  {
    name: 'domain-registry',
    script: 'scripts/governance/check-domain-registry.mjs',
    description: 'Domain registry validation',
  },
  {
    name: 'product-registry-consistency',
    script: 'scripts/governance/check-product-registry-consistency.mjs',
    description: 'Product registry and lifecycle pilot consistency',
  },
  {
    name: 'package-governance',
    script: 'scripts/governance/check-package-governance.mjs',
    description: 'TypeScript platform package governance',
  },
  {
    name: 'boundaries',
    script: 'scripts/governance/check-boundaries.mjs',
    description: 'Architecture boundary enforcement',
  },
  {
    name: 'doc-current-state-claims',
    script: 'scripts/governance/check-doc-current-state-claims.mjs',
    description: 'Documentation authority, truth, and current-state claims',
  },
  {
    name: 'duplication-exceptions',
    script: 'scripts/governance/check-duplication-exceptions.mjs',
    description: 'Duplication exception registry validation',
  },
];

/**
 * Run all governance checks and return a summary of failures.
 * @returns {{ failures: string[], passed: string[] }}
 */
export function runGovernanceChecks() {
  const failures = [];
  const passed = [];

  for (const check of GOVERNANCE_CHECKS) {
    const scriptPath = path.join(repoRoot, check.script);
    process.stdout.write(`\nRunning [${check.name}]: ${check.description}...\n`);

    const result = spawnSync(process.execPath, [scriptPath], {
      stdio: 'inherit',
      cwd: repoRoot,
    });

    if (result.status === 0) {
      passed.push(check.name);
    } else {
      failures.push(check.name);
      process.stdout.write(`FAIL: ${check.name} check failed (exit code ${result.status ?? 'null'}).\n`);
    }
  }

  return { failures, passed };
}

// Run when invoked directly
const isMain = process.argv[1] === fileURLToPath(import.meta.url);
if (isMain) {
  process.stdout.write('=== Ghatana Governance Checks ===\n');

  const { failures, passed } = runGovernanceChecks();

  process.stdout.write('\n=== Governance Check Summary ===\n');
  process.stdout.write(`Passed: ${passed.length} / ${GOVERNANCE_CHECKS.length}\n`);

  if (failures.length === 0) {
    process.stdout.write('OK: all governance checks passed.\n');
    process.exit(0);
  } else {
    process.stdout.write(`FAIL: ${failures.length} governance check(s) failed: ${failures.join(', ')}\n`);
    process.exit(1);
  }
}
