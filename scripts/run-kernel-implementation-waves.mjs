#!/usr/bin/env node

import { mkdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';

import { runTaskMatrixCheck } from './check-kernel-implementation-task-matrix.mjs';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'kernel-implementation-wave-run.json');

const waveDefinitions = {
  'wave-1-foundation': [
    'pnpm:check:product-shape-capability-matrix',
    'pnpm:check:product-registry-drift',
    'pnpm:check:platform-product-boundaries',
    'pnpm:check:cross-product-interaction-boundaries',
    'pnpm:check:kernel-implementation-task-matrix',
    'pnpm:check:kernel-implementation-plan-coverage',
  ],
  'wave-2-runtime-governance': [
    'pnpm:check:route-entitlement-contracts',
    'pnpm:check:atomic-workflow-proof',
    'pnpm:check:atomic-workflow-failure-injection',
    'pnpm:check:runtime-dependency-failure-injection',
    'pnpm:check:runtime-failure-injection',
    'pnpm:check:ai-governance-conformance',
    'pnpm:check:doc-claims-evidence',
    'pnpm:check:current-state-claims',
  ],
  'wave-3-quality-performance': [
    'pnpm:check:data-cloud-ui-a11y',
    'pnpm:check:product-a11y-route-matrix',
    'pnpm:check:i18n-conformance',
    'pnpm:check:product-slo-budgets',
    'pnpm:check:product-cost-budgets',
    'pnpm:check:product-domain-invariants',
    'pnpm:check:openapi-release-quality',
    'pnpm:check:openapi-breaking-changes',
  ],
  'wave-4-release-ops': [
    'pnpm:check:data-cloud-runbook-smoke',
    'pnpm:check:release-rollback-drill',
    'pnpm:check:affected-product-strict-release-profile',
    'pnpm:check:product-release-readiness',
    'pnpm:check:validate-release-evidence',
    'pnpm:check:release-gate',
  ],
};

const heavyCommands = [
  'pnpm:check:phase8:integration',
  'pnpm:check:phase8:e2e',
  'pnpm:check:cross-product-interaction-flows',
  'pnpm:check:interaction-performance',
];

function parseArgs(argv) {
  const options = {
    execute: false,
    includeHeavy: false,
    waves: ['wave-1-foundation', 'wave-2-runtime-governance', 'wave-3-quality-performance', 'wave-4-release-ops'],
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--execute') {
      options.execute = true;
      continue;
    }
    if (arg === '--include-heavy') {
      options.includeHeavy = true;
      continue;
    }
    if (arg === '--waves' && argv[index + 1]) {
      options.waves = argv[index + 1]
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean);
      index += 1;
      continue;
    }
  }

  return options;
}

function runCommand(commandRef) {
  if (commandRef.startsWith('pnpm:')) {
    const scriptName = commandRef.slice('pnpm:'.length);
    if (process.platform === 'win32') {
      return spawnSync('cmd.exe', ['/d', '/c', 'pnpm', scriptName], {
        cwd: repoRoot,
        stdio: 'inherit',
        env: process.env,
      });
    }
    return spawnSync('pnpm', [scriptName], {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
    });
  }

  return spawnSync(process.execPath, [commandRef], {
    cwd: repoRoot,
    stdio: 'inherit',
    env: process.env,
  });
}

function buildExecutionPlan(waves, includeHeavy) {
  const planCommands = [];
  for (const wave of waves) {
    for (const commandRef of waveDefinitions[wave] ?? []) {
      if (!planCommands.includes(commandRef)) {
        planCommands.push(commandRef);
      }
    }
  }

  if (includeHeavy) {
    for (const commandRef of heavyCommands) {
      if (!planCommands.includes(commandRef)) {
        planCommands.push(commandRef);
      }
    }
  }

  return planCommands;
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const matrix = runTaskMatrixCheck({ writeEvidence: true });
  const unknownWaves = options.waves.filter((wave) => !waveDefinitions[wave]);

  if (matrix.violations.length > 0) {
    for (const violation of matrix.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  if (unknownWaves.length > 0) {
    console.error(`Unknown waves requested: ${unknownWaves.join(', ')}`);
    process.exit(1);
  }

  const plannedCommands = buildExecutionPlan(options.waves, options.includeHeavy);
  const commandRuns = [];

  if (options.execute) {
    for (const commandRef of plannedCommands) {
      const startedAt = Date.now();
      const result = runCommand(commandRef);
      const durationMs = Date.now() - startedAt;
      const status = result.status ?? 1;
      commandRuns.push({ command: commandRef, status, durationMs });

      if (status !== 0) {
        break;
      }
    }
  }

  const passed = options.execute
    ? commandRuns.every((run) => run.status === 0)
    : true;

  const report = {
    generatedAt: new Date().toISOString(),
    status: passed ? 'passed' : 'failed',
    mode: options.execute ? 'execute' : 'plan-only',
    includeHeavy: options.includeHeavy,
    selectedWaves: options.waves,
    deferredHeavyCommands: options.includeHeavy ? [] : heavyCommands,
    commandCount: plannedCommands.length,
    plannedCommands,
    commandRuns,
    taskMatrixSummary: matrix.report?.summary ?? null,
    incrementalRoadmap: matrix.report?.incrementalRoadmap ?? null,
  };

  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  if (!passed) {
    console.error(`Kernel implementation wave execution failed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
    process.exit(1);
  }

  console.log(`Kernel implementation wave ${options.execute ? 'execution' : 'plan'} completed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
}

main();
