#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'data-cloud-release-runtime-profile.json');

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
  { name: 'AI governance conformance', command: ['node', './scripts/check-ai-governance-conformance.mjs'] },
  { name: 'i18n conformance', command: ['node', './scripts/check-i18n-conformance.mjs'] },
  { name: 'Route entitlement contracts', command: ['node', './scripts/check-route-entitlement-contracts.mjs'] },
  { name: 'OpenAPI canonical alignment', command: ['node', './scripts/check-openapi-contract-canonical.mjs'] },
  { name: 'Observability conformance', command: ['node', './scripts/check-observability-conformance.mjs'] },
];

const results = [];

for (const check of checks) {
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
writeJsonWithRetry(evidencePath, {
  generatedAt: 'generated-on-demand',
  checks: results,
  pass: results.every((entry) => entry.ok),
});

console.log(`Data Cloud release runtime profile passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
