import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const generateSummaryScript = path.join(repoRoot, 'scripts', 'generate-release-maturity-summary.mjs');
const validateEvidenceScript = path.join(repoRoot, 'scripts', 'validate-release-evidence.mjs');

test('release summary records required gate evaluations and pass consistency', () => {
  execFileSync(process.execPath, [generateSummaryScript], {
    cwd: repoRoot,
    stdio: 'pipe',
    encoding: 'utf8',
  });

  const summaryPath = path.join(repoRoot, 'release-evidence', 'release-summary.json');
  const summary = JSON.parse(readFileSync(summaryPath, 'utf8'));

  assert.ok(summary.releaseGate);
  assert.ok(Array.isArray(summary.releaseGate.requiredGates));

  const requiredGateKeys = summary.releaseGate.requiredGates.map((entry) => entry.gateKey);
  for (const gateKey of ['smoke', 'backup', 'runtimeProfile', 'atomicWorkflow', 'implementationPlanCoverage', 'releaseScoreThresholdPolicy', 'perProductReleaseReadiness']) {
    assert.equal(requiredGateKeys.includes(gateKey), true, `required gate missing: ${gateKey}`);
  }

  const expectedPass = summary.releaseGate.requiredGates.every((entry) => entry.available === true && entry.pass === true);
  assert.equal(summary.pass, expectedPass);
});

test('release evidence validator fails closed when required evidence is missing or non-passing', () => {
  const result = spawnSync(process.execPath, [validateEvidenceScript], {
    cwd: repoRoot,
    encoding: 'utf8',
  });

  assert.notEqual(result.status, 0);
  assert.ok((result.stdout + result.stderr).includes('Release evidence validation failed'));
});
