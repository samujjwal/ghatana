import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..', '..');

function runNodeScript(args) {
  const result = spawnSync(process.execPath, args, {
    cwd: repoRoot,
    encoding: 'utf8',
    shell: false,
  });

  return result;
}

test('kernel-product plan returns the digital-marketing build surfaces', () => {
  const result = runNodeScript([
    join(repoRoot, 'scripts', 'kernel-product.mjs'),
    'product',
    'plan',
    'digital-marketing',
    'build',
    '--json',
  ]);

  assert.equal(result.status, 0, result.stderr);

  const plan = JSON.parse(result.stdout);
  assert.equal(plan.productId, 'digital-marketing');
  assert.equal(plan.phase, 'build');
  assert.deepEqual(
    plan.surfaces.map((surface) => surface.surface),
    ['backend-api', 'web'],
  );
  assert.equal(plan.steps.length, 2);
});

test('kernel-product build dry-run returns skipped execution results', () => {
  const result = runNodeScript([
    join(repoRoot, 'scripts', 'kernel-product.mjs'),
    'product',
    'build',
    'digital-marketing',
    '--dry-run',
    '--json',
  ]);

  assert.equal(result.status, 0, result.stderr);

  const payload = JSON.parse(result.stdout);
  assert.equal(payload.result.status, 'skipped');
  assert.equal(payload.result.steps.length, 2);
  assert.match(payload.result.steps[0].stdout, /^\[DRY-RUN\]/);
});

test('run-product-task delegates lifecycle-enabled builds to kernel execution', () => {
  const result = runNodeScript([
    join(repoRoot, 'scripts', 'run-product-task.mjs'),
    'digital-marketing',
    'build',
    '--dry-run',
  ]);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /"productId":\s*"digital-marketing"/);
  assert.match(result.stdout, /"phase":\s*"build"/);
});

test('kernel lifecycle contract checks succeed', () => {
  const lifecycleCheck = runNodeScript([join(repoRoot, 'scripts', 'check-product-lifecycle-contracts.mjs')]);
  const artifactCheck = runNodeScript([join(repoRoot, 'scripts', 'check-product-artifact-contracts.mjs')]);
  const deploymentCheck = runNodeScript([join(repoRoot, 'scripts', 'check-product-deployment-contracts.mjs')]);

  assert.equal(lifecycleCheck.status, 0, lifecycleCheck.stderr);
  assert.equal(artifactCheck.status, 0, artifactCheck.stderr);
  assert.equal(deploymentCheck.status, 0, deploymentCheck.stderr);
});