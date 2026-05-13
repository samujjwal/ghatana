#!/usr/bin/env node
/**
 * Documentation truth checks for architecture/audit docs.
 *
 * Checks:
 * 1) Audit docs include "Commit audited:" metadata.
 * 2) No absolute local filesystem paths in docs.
 * 3) "verified" claims include nearby test evidence terms.
 *
 * Exit 0 = pass, 1 = fail.
 */

import { readFileSync, readdirSync, statSync, existsSync } from 'fs';
import { join, relative, resolve } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');
const args = process.argv.slice(2);
const CHANGED_ONLY = args.includes('--changed-only');
const BASE_REF_FLAG = args.find((a) => a.startsWith('--base-ref='));
const BASE_REF = BASE_REF_FLAG ? BASE_REF_FLAG.split('=')[1] : 'origin/main';

const DOC_ROOTS = [
  join(REPO_ROOT, 'docs'),
  join(REPO_ROOT, 'code-audits'),
  join(REPO_ROOT, 'products', 'aep', 'docs'),
  join(REPO_ROOT, 'products', 'data-cloud', 'docs'),
];
const DOC_SCOPE_FILE = join(REPO_ROOT, 'config', 'documentation-truth-scope.json');

const ABSOLUTE_PATH_PATTERN = /([A-Za-z]:\\[^\s]+|\/Users\/[^\s]+|\/home\/[^\s]+)/;
const VERIFIED_PATTERN = /\bverified\b/i;
const TEST_EVIDENCE_PATTERN = /\b(test|tests|e2e|integration|contract|coverage|vitest|jest|playwright)\b/i;
const AUDIT_META_PATTERN = /Commit audited:\s*`?[0-9a-f]{7,40}`?/i;
function loadDocumentationScope() {
  if (!existsSync(DOC_SCOPE_FILE)) {
    return null;
  }
  const scope = JSON.parse(readFileSync(DOC_SCOPE_FILE, 'utf8'));
  const includeFiles = new Set((scope.includeFiles ?? []).map((file) => file.replace(/\\/g, '/')));
  const archivedPathSegments = scope.archivedPathSegments ?? [];
  return { includeFiles, archivedPathSegments };
}

/** @param {string} dir */
function walkMarkdownFiles(dir) {
  if (!existsSync(dir)) return [];
  const files = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      files.push(...walkMarkdownFiles(full));
    } else if (entry.toLowerCase().endsWith('.md')) {
      files.push(full);
    }
  }
  return files;
}

/** @returns {Set<string> | null} */
function getChangedFiles() {
  if (!CHANGED_ONLY) return null;
  try {
    const output = execSync(`git diff --name-only ${BASE_REF}...HEAD`, {
      cwd: REPO_ROOT,
      stdio: ['ignore', 'pipe', 'ignore'],
      encoding: 'utf8',
    });
    return new Set(
      output
        .split('\n')
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
        .map((s) => s.replace(/\\/g, '/')),
    );
  } catch {
    console.warn(`Warning: failed to resolve changed files from ${BASE_REF}; scanning full scope.`);
    return null;
  }
}

const changedFiles = getChangedFiles();
const documentationScope = loadDocumentationScope();

const markdownFiles = documentationScope
  ? [...documentationScope.includeFiles].map((file) => join(REPO_ROOT, file)).filter((file) => existsSync(file))
  : DOC_ROOTS.flatMap((root) => walkMarkdownFiles(root));
const failures = [];

for (const file of markdownFiles) {
  const rel = relative(REPO_ROOT, file).replace(/\\/g, '/');
  if (documentationScope?.archivedPathSegments.some((segment) => rel.startsWith(segment))) {
    continue;
  }
  if (CHANGED_ONLY && changedFiles && !changedFiles.has(rel)) {
    continue;
  }
  const text = readFileSync(file, 'utf8');
  const lines = text.split('\n');

  // Rule 1: audit docs should include commit metadata
  if (rel.startsWith('code-audits/') || rel.includes('/audits/')) {
    if (!AUDIT_META_PATTERN.test(text)) {
      failures.push(`${rel}: missing \"Commit audited:\" metadata`);
    }
  }

  // Rule 2: absolute local filesystem paths are forbidden in docs
  lines.forEach((line, idx) => {
    if (ABSOLUTE_PATH_PATTERN.test(line)) {
      failures.push(`${rel}:${idx + 1} contains absolute local path`);
    }
  });

  // Rule 3: "verified" claims need nearby test-evidence wording
  lines.forEach((line, idx) => {
    if (!VERIFIED_PATTERN.test(line)) return;
    const window = lines.slice(Math.max(0, idx - 1), Math.min(lines.length, idx + 2)).join(' ');
    if (!TEST_EVIDENCE_PATTERN.test(window)) {
      failures.push(`${rel}:${idx + 1} has a \"verified\" claim without nearby test evidence`);
    }
  });
}

if (failures.length === 0) {
  console.log('OK: documentation truth checks passed.');
  process.exit(0);
}

console.error('FAIL: documentation truth checks found issues:');
for (const failure of failures) {
  console.error(` - ${failure}`);
}
process.exit(1);
