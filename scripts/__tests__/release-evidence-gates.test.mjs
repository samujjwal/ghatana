import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { evaluateEvidenceFreshness, getFreshnessProfile } from '../validate-release-evidence.mjs';

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

test('release summary generator can target an isolated root', () => {
  const isolatedRoot = mkdtempSync(path.join(os.tmpdir(), 'ghatana-release-summary-'));

  execFileSync(process.execPath, [generateSummaryScript, '--root', isolatedRoot], {
    cwd: isolatedRoot,
    stdio: 'pipe',
    encoding: 'utf8',
    env: {
      ...process.env,
      RELEASE_EVIDENCE_ROOT: isolatedRoot,
    },
  });

  const summaryPath = path.join(isolatedRoot, 'release-evidence', 'release-summary.json');
  const summary = JSON.parse(readFileSync(summaryPath, 'utf8'));

  assert.equal(summary.releaseEnvironment, process.env.RELEASE_ENVIRONMENT ?? 'staging');
  assert.ok(Array.isArray(summary.releaseGate.requiredGates));
});

test('release evidence validator fails closed when required evidence is missing or non-passing', () => {
  const isolatedRoot = mkdtempSync(path.join(os.tmpdir(), 'ghatana-release-evidence-'));

  const result = spawnSync(process.execPath, [validateEvidenceScript], {
    cwd: isolatedRoot,
    encoding: 'utf8',
    env: {
      ...process.env,
      RELEASE_EVIDENCE_ROOT: isolatedRoot,
    },
  });

  assert.notEqual(result.status, 0);
  assert.ok((result.stdout + result.stderr).includes('Release evidence validation failed'));
});

test('release evidence freshness follows the policy map for runtime-production evidence', () => {
  const profile = getFreshnessProfile('.kernel/evidence/product-cost-budgets.json');

  assert.equal(profile.evidenceType, 'runtime-production');
  assert.equal(profile.thresholdHours, 4);
  assert.equal(profile.requireSourceBacking, true);
});

test('release evidence freshness flags stale evidence and requires source backing when mandated', () => {
  const staleGeneratedAt = new Date(Date.now() - (5 * 60 * 60 * 1000)).toISOString();
  const errors = [];

  evaluateEvidenceFreshness('.kernel/evidence/product-cost-budgets.json', {
    generatedAt: staleGeneratedAt,
    sourceCommit: 'abc123',
    sourceBranch: 'main',
  }, errors);

  assert.equal(errors.some((message) => message.includes('stale')), true);
});
