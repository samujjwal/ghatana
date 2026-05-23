import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import path from 'node:path';

const repoRoot = process.cwd();
const scriptPath = path.join(repoRoot, 'scripts/run-kernel-implementation-waves.mjs');

test('kernel implementation wave planner supports plan-only mode with deferred heavy suites', () => {
  const run = spawnSync(process.execPath, [scriptPath, '--waves', 'wave-1-foundation,wave-2-runtime-governance'], {
    cwd: repoRoot,
    encoding: 'utf8',
    env: process.env,
  });

  assert.equal(run.status, 0, run.stdout + run.stderr);
  assert.match(run.stdout, /Kernel implementation wave plan completed/);
});
