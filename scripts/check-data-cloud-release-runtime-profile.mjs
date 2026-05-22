#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'data-cloud-release-runtime-profile.json');

const checks = [
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
writeFileSync(
  evidencePath,
  `${JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      checks: results,
      pass: results.every((entry) => entry.ok),
    },
    null,
    2,
  )}\n`,
  'utf8',
);

console.log(`Data Cloud release runtime profile passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
