#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const CRITICAL_EVIDENCE = [
  {
    path: '.kernel/evidence/product-release-readiness.json',
    expectedSource: 'scripts/check-product-release-readiness.mjs',
    expectedCommand: 'pnpm check:product-release-readiness',
  },
  {
    path: '.kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json',
    expectedSource: 'scripts/check-ai-governance-behavioral-proof.mjs',
    expectedCommand: 'pnpm check:ai-governance-behavioral-proof',
  },
  {
    path: '.kernel/evidence/data-cloud-active-modules.json',
    expectedSource: 'scripts/generate-data-cloud-active-modules-evidence.mjs',
    expectedCommand: 'pnpm check:data-cloud-active-module-evidence',
  },
  {
    path: '.kernel/evidence/action-plane-boundaries.json',
    expectedSource: 'scripts/check-action-plane-boundaries.mjs',
    expectedCommand: 'pnpm check:action-plane-boundaries',
  },
];

function parseJson(filePath) {
  try {
    return JSON.parse(readFileSync(filePath, 'utf8'));
  } catch (error) {
    return { error };
  }
}

function currentGitSha(root) {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return null;
  }
}

export function checkEvidenceRunMetadata(root = process.cwd(), evidenceFiles = CRITICAL_EVIDENCE) {
  const violations = [];
  const expectedCommit = currentGitSha(root);
  for (const evidence of evidenceFiles) {
    const evidencePath = path.join(root, evidence.path);
    if (!existsSync(evidencePath)) {
      violations.push(`${evidence.path}: evidence file is missing`);
      continue;
    }

    const payload = parseJson(evidencePath);
    if (payload.error) {
      violations.push(`${evidence.path}: evidence is not valid JSON (${payload.error.message})`);
      continue;
    }

    const run = payload.evidenceRun;
    if (!run || typeof run !== 'object') {
      violations.push(`${evidence.path}: missing evidenceRun metadata`);
      continue;
    }

    if (run.generatedBy !== evidence.expectedSource) {
      violations.push(`${evidence.path}: evidenceRun.generatedBy must be ${evidence.expectedSource}`);
    }
    if (run.source !== evidence.expectedSource) {
      violations.push(`${evidence.path}: evidenceRun.source must be ${evidence.expectedSource}`);
    }
    if (run.command !== evidence.expectedCommand) {
      violations.push(`${evidence.path}: evidenceRun.command must be ${evidence.expectedCommand}`);
    }
    if (typeof run.commit !== 'string' || !/^[a-f0-9]{40}$|^unknown$/i.test(run.commit)) {
      violations.push(`${evidence.path}: evidenceRun.commit must be a 40-character git SHA`);
    }
    if (expectedCommit && run.commit !== expectedCommit) {
      violations.push(`${evidence.path}: evidenceRun.commit must match current HEAD ${expectedCommit}`);
    }
    if (!existsSync(path.join(root, run.source ?? ''))) {
      violations.push(`${evidence.path}: evidenceRun.source does not exist`);
    }
    if (typeof payload.generatedAt !== 'string' && typeof payload.timestamp !== 'string') {
      violations.push(`${evidence.path}: generatedAt or timestamp is required`);
    }
  }
  return violations;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const violations = checkEvidenceRunMetadata(root);
  if (violations.length === 0) {
    console.log('Evidence run metadata check passed.');
    return;
  }

  console.error('Evidence run metadata check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
