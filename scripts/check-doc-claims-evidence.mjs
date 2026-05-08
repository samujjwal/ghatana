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

/**
 * Audit-tracker-specific files that must have evidence references on every
 * "Completed" or "Partial (fixed)" status row.
 */
const AUDIT_TRACKER_FILES = [
  join(REPO_ROOT, 'docs', 'kernel', 'PRODUCT_KERNEL_AUDIT_PROGRESS.md'),
];

/**
 * Matches a Markdown table row whose status column is "Partial (fixed ...)".
 * These rows MUST cite a source file or script path as evidence.
 * "Completed" rows are warned but not failed (evidence backfill in progress).
 */
const AUDIT_ROW_PARTIAL_FIXED_PATTERN = /^\|\s*\d+\s*\|\s*Partial\s*\(fixed[^)]*\)\s*\|/i;
const AUDIT_ROW_COMPLETED_PATTERN = /^\|\s*\d+\s*\|\s*Completed\s*\|/i;

/**
 * An evidence reference inside an audit row — a path segment that points to a
 * real file location.
 */
const AUDIT_ROW_EVIDENCE_PATTERN =
  /`?(products\/|scripts\/|src\/|\.github\/workflows\/|platform\/|config\/)[^\s`|,]+`?/i;

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
 * Audit tracker evidence validation
 ***********************/

const warnings = [];

for (const trackerFile of AUDIT_TRACKER_FILES) {
  if (!existsSync(trackerFile)) {
    failures.push(`Audit tracker file missing: ${relative(REPO_ROOT, trackerFile).replace(/\\/g, '/')}`);
    continue;
  }

  const relPath = relative(REPO_ROOT, trackerFile).replace(/\\/g, '/');
  if (CHANGED_ONLY && changedFiles && !changedFiles.has(relPath)) {
    continue;
  }

  const text = readFileSync(trackerFile, 'utf8');
  const lines = text.split('\n');

  lines.forEach((line, index) => {
    const isPartialFixed = AUDIT_ROW_PARTIAL_FIXED_PATTERN.test(line);
    const isCompleted = AUDIT_ROW_COMPLETED_PATTERN.test(line);

    if (!isPartialFixed && !isCompleted) {
      return;
    }

    const hasEvidence = AUDIT_ROW_EVIDENCE_PATTERN.test(line);

    if (!hasEvidence) {
      if (isPartialFixed) {
        // Partial (fixed) rows MUST have evidence — hard failure
        failures.push(
          `${relPath}:${index + 1} "Partial (fixed)" row is missing an evidence path reference (need "products/...", "scripts/...", etc.): ${line.slice(0, 80).trim()}`,
        );
      } else {
        // Completed rows should have evidence — warn only (backfill in progress)
        warnings.push(
          `${relPath}:${index + 1} "Completed" row has no evidence path reference (backfill needed): ${line.slice(0, 80).trim()}`,
        );
      }
    }
  });
}

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

if (failures.length === 0 && warnings.length === 0) {
  console.log('OK: documentation claim-evidence checks passed.');
  process.exit(0);
}

if (warnings.length > 0) {
  console.warn('WARN: audit tracker rows missing evidence references (backfill in progress):');
  for (const warning of warnings) {
    console.warn(` - ${warning}`);
  }
}

if (failures.length === 0) {
  console.log('OK: no hard failures. See warnings above for evidence backfill items.');
  process.exit(0);
}

console.error('FAIL: documentation claim-evidence checks found issues:');
for (const failure of failures) {
  console.error(` - ${failure}`);
}
process.exit(1);
