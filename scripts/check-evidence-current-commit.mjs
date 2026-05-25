#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

const DEFAULT_EVIDENCE_ROOT = '.kernel/evidence';

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

function walkJsonFiles(root, relativePath, files) {
  const fullPath = path.join(root, relativePath);
  if (!existsSync(fullPath)) {
    return;
  }

  const stats = statSync(fullPath);
  if (stats.isFile()) {
    if (path.extname(fullPath) === '.json') {
      files.push(relativePath.replaceAll(path.sep, '/'));
    }
    return;
  }

  for (const entry of readdirSync(fullPath)) {
    walkJsonFiles(root, path.join(relativePath, entry), files);
  }
}

function parseJson(filePath) {
  try {
    return JSON.parse(readFileSync(filePath, 'utf8'));
  } catch (error) {
    return { error };
  }
}

export function findEvidenceCurrentCommitViolations(
  root = process.cwd(),
  {
    evidenceRoot = DEFAULT_EVIDENCE_ROOT,
    expectedCommit = currentGitSha(root),
  } = {},
) {
  const violations = [];
  if (!expectedCommit) {
    violations.push('Unable to resolve current git HEAD for evidence freshness check');
    return violations;
  }

  const evidenceFiles = [];
  walkJsonFiles(root, evidenceRoot, evidenceFiles);

  for (const evidenceFile of evidenceFiles) {
    const payload = parseJson(path.join(root, evidenceFile));
    if (payload.error) {
      violations.push(`${evidenceFile}: evidence JSON is invalid (${payload.error.message})`);
      continue;
    }

    const commit = payload?.evidenceRun?.commit;
    if (commit === undefined) {
      continue;
    }
    if (typeof commit !== 'string' || !/^[a-f0-9]{40}$/i.test(commit)) {
      violations.push(`${evidenceFile}: evidenceRun.commit must be a 40-character git SHA`);
      continue;
    }
    if (commit !== expectedCommit) {
      violations.push(`${evidenceFile}: evidenceRun.commit ${commit} must match current HEAD ${expectedCommit}`);
    }
  }

  return violations;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const evidenceRootArgIndex = process.argv.indexOf('--evidence-root');
  const evidenceRoot = evidenceRootArgIndex >= 0
    ? process.argv[evidenceRootArgIndex + 1]
    : DEFAULT_EVIDENCE_ROOT;
  const summaryOnly = process.argv.includes('--summary');
  const violations = findEvidenceCurrentCommitViolations(root, { evidenceRoot });

  if (violations.length === 0) {
    console.log('Evidence current-commit check passed.');
    if (summaryOnly) {
      console.log('\n✓ All evidence files are current and match HEAD.');
    }
    return;
  }

  if (summaryOnly) {
    console.log('Stale evidence:');
    for (const violation of violations) {
      console.log(`- ${violation}`);
    }
    process.exit(1);
  }

  console.error('Evidence current-commit check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
