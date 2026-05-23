import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const scriptPath = path.join(repoRoot, 'scripts/check-kernel-implementation-task-matrix.mjs');

test('kernel implementation task matrix parses all 50 plan items', () => {
  const run = spawnSync(process.execPath, [scriptPath], {
    cwd: repoRoot,
    encoding: 'utf8',
    env: process.env,
  });

  assert.equal(run.status, 0, run.stdout + run.stderr);
  assert.match(run.stdout, /Kernel implementation task matrix check passed/);

  const evidencePath = path.join(repoRoot, '.kernel/evidence/kernel-implementation-task-matrix.json');
  const evidence = JSON.parse(readFileSync(evidencePath, 'utf8'));

  assert.equal(evidence.summary.requiredTaskCount, 50);
  assert.equal(evidence.incrementalRoadmap.targetMaturityDepth, 5);
  assert.ok(Array.isArray(evidence.incrementalRoadmap.tasks));
  assert.ok(evidence.incrementalRoadmap.tasks.length >= 1);
});
