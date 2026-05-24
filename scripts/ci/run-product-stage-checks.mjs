#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

import { loadCanonicalRegistry, resolveAffectedProducts } from '../resolve-affected-products.mjs';
import {
  buildStagePlan,
  parseStage,
  readChangedFiles,
  repoRoot,
  splitCsv,
} from './product-stage-plan.mjs';

const argv = process.argv.slice(2);

function argValue(name) {
  const index = argv.indexOf(name);
  return index >= 0 ? argv[index + 1] : undefined;
}

function hasFlag(name) {
  return argv.includes(name);
}

function usage() {
  console.log([
    'Usage: node scripts/ci/run-product-stage-checks.mjs --stage <dev|validate|test|build|package|release> [options]',
    '',
    'Options:',
    '  --base <ref>              Git diff base',
    '  --head <ref>              Git diff head',
    '  --paths file1,file2       Explicit changed-file list',
    '  --products id,id          Explicit product scope',
    '  --stdin                   Read changed files from stdin',
    '  --include-dependencies    Add declared product platform-provider dependency checks',
    '  --release-risk            Treat product changes as release-relevant',
    '  --expensive               Allow expensive E2E/performance/security stage checks',
    '  --full                    Run the broadest stage profile',
    '  --dry-run                 Print planned commands without executing',
    '  --json                    Emit the plan as JSON',
  ].join('\n'));
}

function runCommand(command) {
  console.log(`\n==> ${command.label}`);
  console.log([command.cmd, ...command.args].join(' '));
  const result = spawnSync(command.cmd, command.args, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: command.shell === true || process.platform === 'win32',
    env: process.env,
  });

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function main() {
  if (hasFlag('--help')) {
    usage();
    return;
  }

  const stage = parseStage(argValue('--stage') || argValue('-s') || process.env.CHECK_STAGE || 'validate');
  const explicitProducts = splitCsv(argValue('--products') || process.env.AFFECTED_PRODUCTS);
  const hasExplicitFileScope = Boolean(argValue('--paths') || argValue('--base') || argValue('--head') || hasFlag('--stdin'));
  const files = hasExplicitFileScope || explicitProducts.length === 0
    ? readChangedFiles({ argv, cwd: repoRoot })
    : explicitProducts.map((productId) => `products/${productId}/`);
  const registry = loadCanonicalRegistry(repoRoot);
  const affected = explicitProducts.length > 0
    ? {
        changedFiles: files,
        affectedProducts: explicitProducts,
        products: explicitProducts.map((productId) => ({ productId, reasons: ['explicit-products'] })),
        docsOnly: false,
      }
    : resolveAffectedProducts(files, registry, { businessProductsOnly: false, includeDemo: false });
  const packageJson = JSON.parse(readFileSync(path.join(repoRoot, 'package.json'), 'utf8'));
  const plan = buildStagePlan({
    stage,
    files,
    products: affected.affectedProducts,
    registry,
    packageJson,
    includeDependencies: hasFlag('--include-dependencies'),
    releaseRisk: hasFlag('--release-risk') || process.env.RELEASE_RISK === 'true',
    expensive: hasFlag('--expensive') || process.env.RUN_EXPENSIVE_CHECKS === 'true',
    full: hasFlag('--full') || process.env.FULL_CHECK === 'true' || (!hasExplicitFileScope && explicitProducts.length > 0),
  });

  if (hasFlag('--json')) {
    console.log(JSON.stringify({ affected, plan }, null, 2));
    return;
  }

  console.log(`Product stage: ${stage}`);
  console.log(`Affected products: ${affected.affectedProducts.length > 0 ? affected.affectedProducts.join(', ') : '(none)'}`);

  if (plan.commands.length === 0) {
    console.log('No commands planned for this stage and change set.');
    return;
  }

  if (hasFlag('--dry-run')) {
    for (const command of plan.commands) {
      console.log([command.cmd, ...command.args].join(' '));
    }
    return;
  }

  for (const command of plan.commands) {
    runCommand(command);
  }
}

try {
  main();
} catch (error) {
  console.error(`Product stage checks failed: ${error.message}`);
  process.exit(1);
}
