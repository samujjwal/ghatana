#!/usr/bin/env node
/**
 * Docs claim-evidence lint.
 *
 * Fails when documentation contains high-confidence claims without nearby
 * objective evidence terms.
 *
 * Claims checked:
 * - "ready for production"
 * - "verified"
 * - "validated"
 *
 * Evidence terms checked in a +/- 1 line window:
 * - test/tests/e2e/integration/contract/coverage/vitest/jest/playwright
 * - ci/build/check/lint/typecheck/jacoco/report/evidence
 */

import { existsSync, readdirSync, readFileSync, statSync } from 'fs';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';
import { join, relative, resolve } from 'path';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');
const args = process.argv.slice(2);
const CHANGED_ONLY = args.includes('--changed-only');
const BASE_REF_ARG = args.find((arg) => arg.startsWith('--base-ref='));
const BASE_REF = BASE_REF_ARG ? BASE_REF_ARG.split('=')[1] : 'origin/main';

const DOC_ROOTS = [
  join(REPO_ROOT, 'docs'),
  join(REPO_ROOT, 'code-audits'),
  join(REPO_ROOT, 'products', 'data-cloud', 'docs'),
  join(REPO_ROOT, 'products', 'aep', 'docs'),
];

const CLAIM_PATTERNS = [
  /\bready for production\b/i,
  /\bverified\b/i,
  /\bvalidated\b/i,
];

const EVIDENCE_PATTERN =
  /\b(test|tests|e2e|integration|contract|coverage|vitest|jest|playwright|ci|build|check|lint|typecheck|jacoco|report|evidence)\b/i;

/***********************
 * Utility helpers
 ***********************/

/** @param {string} dir */
function walkMarkdownFiles(dir) {
  if (!existsSync(dir)) {
    return [];
  }

  const files = [];
  for (const entry of readdirSync(dir)) {
    const fullPath = join(dir, entry);
    const stats = statSync(fullPath);

    if (stats.isDirectory()) {
      files.push(...walkMarkdownFiles(fullPath));
      continue;
    }

    if (entry.toLowerCase().endsWith('.md')) {
      files.push(fullPath);
    }
  }

  return files;
}

/** @returns {Set<string> | null} */
function getChangedFiles() {
  if (!CHANGED_ONLY) {
    return null;
  }

  try {
    const output = execSync(`git diff --name-only ${BASE_REF}...HEAD`, {
      cwd: REPO_ROOT,
      stdio: ['ignore', 'pipe', 'ignore'],
      encoding: 'utf8',
    });

    return new Set(
      output
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.length > 0)
        .map((line) => line.replace(/\\/g, '/')),
    );
  } catch {
    console.warn(`Warning: failed to resolve changed files from ${BASE_REF}; scanning full docs scope.`);
    return null;
  }
}

function hasClaim(line) {
  return CLAIM_PATTERNS.some((pattern) => pattern.test(line));
}

/***********************
 * Main execution
 ***********************/

const changedFiles = getChangedFiles();
const markdownFiles = DOC_ROOTS.flatMap((root) => walkMarkdownFiles(root));
const failures = [];

for (const markdownFile of markdownFiles) {
  const relPath = relative(REPO_ROOT, markdownFile).replace(/\\/g, '/');
  if (CHANGED_ONLY && changedFiles && !changedFiles.has(relPath)) {
    continue;
  }

  const text = readFileSync(markdownFile, 'utf8');
  const lines = text.split('\n');

  lines.forEach((line, index) => {
    if (!hasClaim(line)) {
      return;
    }

    const start = Math.max(0, index - 1);
    const end = Math.min(lines.length, index + 2);
    const window = lines.slice(start, end).join(' ');

    if (!EVIDENCE_PATTERN.test(window)) {
      failures.push(
        `${relPath}:${index + 1} contains a strong claim without nearby test/build evidence`,
      );
    }
  });
}

if (failures.length === 0) {
  console.log('OK: documentation claim-evidence checks passed.');
  process.exit(0);
}

console.error('FAIL: documentation claim-evidence checks found issues:');
for (const failure of failures) {
  console.error(` - ${failure}`);
}
process.exit(1);
