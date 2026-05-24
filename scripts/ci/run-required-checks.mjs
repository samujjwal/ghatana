#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

import { loadCanonicalRegistry, resolveAffectedProducts } from '../resolve-affected-products.mjs';

const repoRoot = path.resolve(new URL('../..', import.meta.url).pathname);
const argv = process.argv.slice(2);
const releaseRisk = argv.includes('--release-risk') || process.env.RELEASE_RISK === 'true';
const full = argv.includes('--full') || process.env.FULL_CHECK === 'true';
const expensive = argv.includes('--expensive') || process.env.RUN_EXPENSIVE_CHECKS === 'true';

function argValue(name) {
  const index = argv.indexOf(name);
  return index >= 0 ? argv[index + 1] : undefined;
}

function splitCsv(value) {
  return String(value ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function run(label, cmd, args, opts = {}) {
  console.log(`\n==> ${label}`);
  console.log([cmd, ...args].join(' '));
  const result = spawnSync(cmd, args, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: process.platform === 'win32',
    env: process.env,
    ...opts,
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function runPnpm(label, args) {
  run(label, 'pnpm', args);
}

function readStdin() {
  return readFileSync(0, 'utf8')
    .split(/\r?\n/)
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function changedFiles() {
  const pathsArg = argValue('--paths');
  if (pathsArg) {
    return splitCsv(pathsArg);
  }

  if (argv.includes('--stdin')) {
    return readStdin();
  }

  const base = argValue('--base') || process.env.GITHUB_BASE_SHA || process.env.GITHUB_EVENT_BEFORE || 'origin/main';
  const head = argValue('--head') || process.env.GITHUB_SHA || 'HEAD';
  const result = spawnSync('git', ['diff', '--name-only', `${base}...${head}`], {
    cwd: repoRoot,
    encoding: 'utf8',
  });

  if (result.status !== 0) {
    console.error(`Could not resolve diff ${base}...${head}. Refusing to run an unsafe empty scoped check.`);
    if (result.stderr) {
      console.error(result.stderr.trim());
    }
    process.exit(result.status ?? 1);
  }

  return result.stdout.split(/\r?\n/).filter(Boolean);
}

function isGlobalImpact(files) {
  const globalFiles = new Set([
    'package.json',
    'pnpm-lock.yaml',
    'pnpm-workspace.yaml',
    'settings.gradle.kts',
    'build.gradle.kts',
    'gradle.properties',
  ]);
  return files.some((file) =>
    /^\.github\/workflows\//.test(file)
    || /^scripts\//.test(file)
    || /^config\//.test(file)
    || /^gradle\//.test(file)
    || /^platform-kernel\//.test(file)
    || globalFiles.has(file)
  );
}

function docsCheckFiles(files) {
  return files.filter((file) => /\.(?:md|mdx|adoc|txt|json|ya?ml)$/i.test(file));
}

function changedProductFiles(files, productId) {
  return files.filter((file) => file.startsWith(`products/${productId}/`));
}

function directlyTouchedProducts(files, products) {
  return products.filter((productId) => changedProductFiles(files, productId).length > 0);
}

function hasTestImpact(files) {
  return files.some((file) =>
    /(?:^|\/|\.)(?:__tests__|test|tests|spec|e2e|playwright)(?:\/|\.|-)/i.test(file)
    || /\.(?:cjs|cts|java|js|jsx|kts|mjs|mts|proto|sql|ts|tsx)$/i.test(file)
  );
}

function hasBuildImpact(files) {
  return files.some((file) =>
    /\.(?:cjs|cts|java|js|jsx|json|kts|mjs|mts|proto|sql|ts|tsx|yaml|yml)$/i.test(file)
    || /package\.json|pnpm-lock\.yaml|vite|webpack|rollup|gradle|Dockerfile|container|compose/i.test(file)
  );
}

function hasReleaseImpact(files) {
  return files.some((file) => /release|deploy|rollback|evidence|kernel-product|lifecycle|promotion/i.test(file));
}

function hasExpensiveImpact(files) {
  return files.some((file) => /e2e|playwright|performance|load|durable|testcontainers|integration-tests/i.test(file));
}

function runStage(stage, products, extraArgs = []) {
  const scopedFiles = files.length > 0 ? files : products.map((productId) => `products/${productId}/`);
  const args = [
    './scripts/ci/run-product-stage-checks.mjs',
    '--stage',
    stage,
    '--paths',
    scopedFiles.join(','),
    ...extraArgs,
  ];

  if (products.length > 0) {
    args.push('--products', products.join(','));
  }

  run(`product ${stage} stage`, 'node', args);
}

const files = changedFiles();
const explicitProducts = splitCsv(argValue('--products') || process.env.AFFECTED_PRODUCTS);
const registry = loadCanonicalRegistry(repoRoot);
const affected = explicitProducts.length > 0
  ? {
      changedFiles: files,
      affectedProducts: explicitProducts,
      products: explicitProducts.map((productId) => ({ productId, reasons: ['explicit-products'] })),
      docsOnly: false,
    }
  : resolveAffectedProducts(files, registry, { businessProductsOnly: false, includeDemo: false });

console.log('Required-check scope:');
console.log(JSON.stringify(affected, null, 2));

if (files.length === 0 && explicitProducts.length === 0) {
  console.log('No changed files detected; required checks are a no-op.');
  process.exit(0);
}

if (affected.docsOnly) {
  const checkFiles = docsCheckFiles(files);
  if (checkFiles.length > 0) {
    runPnpm('docs format check', ['exec', 'prettier', '--check', ...checkFiles]);
  } else {
    console.log('Docs-only change set has no formatter-supported files; skipping docs check.');
  }
  process.exit(0);
}

if (full || isGlobalImpact(files)) {
  runPnpm('aggregate gate integrity', ['check:aggregate-gate-integrity']);
  runPnpm('architecture boundaries', ['check:architecture-boundaries']);
}

const productsForStage = explicitProducts.length > 0
  ? explicitProducts
  : directlyTouchedProducts(files, affected.affectedProducts);
const stageArgs = ['--include-dependencies'];

runStage('dev', productsForStage);
runStage('validate', productsForStage, stageArgs);

if (full || hasTestImpact(files)) {
  runStage('test', productsForStage, expensive || hasExpensiveImpact(files) ? [...stageArgs, '--expensive'] : stageArgs);
}

if (full || hasBuildImpact(files)) {
  runStage('build', productsForStage, stageArgs);
}

if (releaseRisk || hasReleaseImpact(files)) {
  runStage('release', productsForStage, releaseRisk ? [...stageArgs, '--release-risk'] : stageArgs);
}

console.log('\nRequired checks passed.');
