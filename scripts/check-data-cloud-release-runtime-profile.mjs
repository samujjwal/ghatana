#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'data-cloud-release-runtime-profile.json');
const targetEnvironment = process.env.RELEASE_ENVIRONMENT ?? 'staging';
const evidenceValidityHours = Number(process.env.RELEASE_EVIDENCE_VALIDITY_HOURS ?? '48');
const bootstrapMode = process.env.DATACLOUD_RELEASE_GATE_BOOTSTRAP === 'product-release-readiness';

function currentGitSha() {
  const result = spawnSync('git', ['rev-parse', 'HEAD'], {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  });
  return result.status === 0 ? result.stdout.trim() : 'unknown';
}

function sleepMs(durationMs) {
  const signal = new Int32Array(new SharedArrayBuffer(4));
  Atomics.wait(signal, 0, 0, durationMs);
}

function writeJsonWithRetry(targetPath, payload, maxAttempts = 8) {
  let lastError = null;
  const tempPath = `${targetPath}.tmp`;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      writeFileSync(tempPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
      renameSync(tempPath, targetPath);
      return;
    } catch (error) {
      lastError = error;
      try {
        if (existsSync(tempPath)) {
          unlinkSync(tempPath);
        }
      } catch {
        // Best-effort cleanup only.
      }

      const code = String(error?.code ?? '');
      const retriable = code === 'UNKNOWN' || code === 'EACCES' || code === 'EBUSY' || code === 'EPERM';
      if (!retriable || attempt === maxAttempts) {
        throw error;
      }
      sleepMs(50 * attempt);
    }
  }

  throw lastError;
}

const checks = [
  { name: 'Runtime dependency failure-injection executable', command: ['node', './scripts/check-runtime-dependency-failure-injection.mjs', '--ci', '--product=data-cloud'] },
  { name: 'Atomic workflow failure-injection executable', command: ['node', './scripts/check-atomic-workflow-failure-injection.mjs', '--ci', '--product=data-cloud'] },
  { name: 'Runtime failure-injection posture', command: ['node', './scripts/check-runtime-failure-injection.mjs'] },
  { name: 'Atomic workflow proof', command: ['node', './scripts/check-atomic-workflow-proof.mjs'] },
  { name: 'Data Cloud maturity proof', command: ['node', './scripts/check-data-cloud-maturity-proof.mjs'] },
  { name: 'AI governance conformance', command: ['node', './scripts/check-ai-governance-conformance.mjs'] },
  { name: 'AI governance behavioral proof', command: ['node', './scripts/check-ai-governance-behavioral-proof.mjs', '--ci', '--product=data-cloud'] },
  { name: 'i18n conformance', command: ['node', './scripts/check-i18n-conformance.mjs'] },
  { name: 'i18n behavioral proof', command: ['node', './scripts/check-i18n-behavioral-proof.mjs', '--ci', '--product=data-cloud'] },
  { name: 'a11y behavioral proof', command: ['node', './scripts/check-a11y-behavioral-proof.mjs', '--ci', '--product=data-cloud'] },
  { name: 'Route entitlement contracts', command: ['node', './scripts/check-route-entitlement-contracts.mjs'] },
  { name: 'Action Plane route lifecycle', command: ['node', './scripts/check-action-plane-route-lifecycle.mjs'] },
  { name: 'OpenAPI canonical alignment', command: ['node', './scripts/check-openapi-contract-canonical.mjs'] },
  { name: 'Observability conformance', command: ['node', './scripts/check-observability-conformance.mjs'] },
];

const bootstrapSkippedChecks = new Set([
  'Runtime dependency failure-injection executable',
  'Atomic workflow failure-injection executable',
  'AI governance behavioral proof',
  'i18n behavioral proof',
  'a11y behavioral proof',
]);

const checksToRun = bootstrapMode ? checks.filter((check) => !bootstrapSkippedChecks.has(check.name)) : checks;

if (bootstrapMode) {
  console.warn('Bootstrap mode enabled: skipping heavy executable runtime profile checks.');
}

const results = [];

for (const check of checksToRun) {
  const [bin, ...args] = check.command;
  const run = spawnSync(bin, args, {
    cwd: repoRoot,
    stdio: 'inherit',
    env: process.env,
  });

  const ok = run.status === 0;
  results.push({ check: check.name, ok });
  if (!ok) {
    console.error(`Release runtime profile failed at: ${check.name}`);
    process.exit(run.status ?? 1);
  }
}

mkdirSync(evidenceDir, { recursive: true });
const generatedAt = new Date();
const sourceCommitSha = currentGitSha();
const targetCommitSha = process.env.TARGET_COMMIT_SHA ?? process.env.AUDIT_TARGET_COMMIT ?? sourceCommitSha;
writeJsonWithRetry(evidencePath, {
  generatedAt: generatedAt.toISOString(),
  evidenceRun: {
    generatedBy: 'scripts/check-data-cloud-release-runtime-profile.mjs',
    command: 'pnpm check:data-cloud-release-runtime-profile',
    source: 'scripts/check-data-cloud-release-runtime-profile.mjs',
    commit: sourceCommitSha,
    sourceCommitSha,
    targetCommitSha,
    targetEnvironment,
  },
  sourceCommitSha,
  targetCommitSha,
  targetEnvironment,
  validationStatus: results.every((entry) => entry.ok) ? 'validated' : 'failed',
  reviewDueAt: new Date(generatedAt.getTime() + 24 * 60 * 60 * 1000).toISOString(),
  expiresAt: new Date(generatedAt.getTime() + evidenceValidityHours * 60 * 60 * 1000).toISOString(),
  checks: results,
  bootstrapMode,
  skippedChecks: bootstrapMode ? [...bootstrapSkippedChecks] : [],
  pass: results.every((entry) => entry.ok),
});

console.log(`Data Cloud release runtime profile passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
