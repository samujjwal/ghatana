#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');

const surfaceAliases = new Map([
  ['gateway', 'backend-api'],
  ['backend', 'backend-api'],
  ['api', 'backend-api'],
]);

/**
 * Map legacy task names to lifecycle phases
 */
const taskToPhaseMap = new Map([
  ['dev', 'dev'],
  ['build', 'build'],
  ['test', 'test'],
  ['lint', 'validate'],
  ['typecheck', 'validate'],
]);

function usage(exitCode = 1) {
  const stream = exitCode === 0 ? process.stdout : process.stderr;
  stream.write('Usage: pnpm product <productId> <task> [surface] [--dry-run] [--plan]\n');
  process.exit(exitCode);
}

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function run(command, args, options = {}) {
  if (options.dryRun) {
    console.log([command, ...args].join(' '));
    return;
  }

  const result = spawnSync(command, args, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: false,
  });

  if (result.error) {
    throw result.error;
  }
  process.exit(result.status ?? 1);
}

function normalizeSurfaceName(surfaceName) {
  return surfaceAliases.get(surfaceName) ?? surfaceName;
}

function surfaceGradleModule(product, surface) {
  const byPath = `:${surface.path.replace(/\//g, ':')}`;
  if (product.gradleModules?.includes(byPath)) {
    return byPath;
  }

  const tail = surface.path.split('/').at(-1);
  return product.gradleModules?.find((modulePath) => modulePath.endsWith(`:${tail}`)) ?? product.gradleModules?.[0];
}

function runPnpmPackageTask(product, task, dryRun) {
  const packagePatterns = product.pnpmPackages ?? [];
  if (packagePatterns.length === 0) {
    return false;
  }

  const args = ['-r'];
  for (const packagePattern of packagePatterns) {
    args.push('--filter', `./${packagePattern}`);
  }
  args.push(task);
  run('pnpm', args, { dryRun });
  return true;
}

function runGradleTask(product, task, surface, dryRun) {
  if (task === 'dev') {
    throw new Error(`Product ${product.id} surface ${surface?.type ?? 'all'} does not expose a pnpm dev package`);
  }

  const modulePath = surface ? surfaceGradleModule(product, surface) : product.gradleModules?.[0];
  if (!modulePath) {
    throw new Error(`Product ${product.id} has no pnpm packages or Gradle modules for task ${task}`);
  }

  run('./gradlew', [`${modulePath}:${task}`, '--no-daemon'], { dryRun });
  return true;
}

function main() {
  const args = process.argv.slice(2);
  if (args.includes('--help') || args.length < 2) {
    usage(args.includes('--help') ? 0 : 1);
  }

  const dryRun = args.includes('--dry-run');
  const planOnly = args.includes('--plan');
  const positional = args.filter((arg) => arg !== '--dry-run' && arg !== '--plan');
  const [productId, task, requestedSurface] = positional;
  const registry = loadRegistry();
  const product = registry[productId];

  if (!product) {
    throw new Error(`Unknown product ${productId}`);
  }

  // Check if product has lifecycle enabled - delegate to lifecycle engine
  if (product.lifecycleProfile && product.lifecycle?.enabled) {
    const phase = taskToPhaseMap.get(task);
    if (phase) {
      console.log(`Product ${productId} has lifecycle enabled, delegating to lifecycle engine...`);
      const lifecycleArgs = planOnly ? ['product', 'plan', productId, phase] : ['product', phase, productId];
      if (dryRun) lifecycleArgs.push('--dry-run');
      if (requestedSurface) lifecycleArgs.push('--surface', normalizeSurfaceName(requestedSurface));
      
      const result = spawnSync('node', [join(repoRoot, 'scripts/kernel-product.mjs'), ...lifecycleArgs], {
        cwd: repoRoot,
        stdio: 'inherit',
        shell: false,
      });

      if (result.error) {
        throw result.error;
      }
      process.exit(result.status ?? 1);
    }
    // If task doesn't map to a phase, fall through to legacy logic
  }

  // Legacy behavior for products without lifecycle or non-lifecycle tasks
  if (requestedSurface) {
    const surfaceType = normalizeSurfaceName(requestedSurface);
    const surface = (product.surfaces ?? []).find((candidate) => candidate.type === surfaceType);
    if (!surface) {
      throw new Error(`Product ${productId} does not declare surface ${requestedSurface}`);
    }

    if (surface.packagePath && existsSync(join(repoRoot, surface.packagePath))) {
      run('pnpm', ['--dir', surface.path, task], { dryRun });
      return;
    }

    runGradleTask(product, task, surface, dryRun);
    return;
  }

  if (runPnpmPackageTask(product, task, dryRun)) {
    return;
  }

  runGradleTask(product, task, undefined, dryRun);
}

try {
  main();
} catch (error) {
  console.error(`Product task failed: ${error.message}`);
  process.exit(1);
}
