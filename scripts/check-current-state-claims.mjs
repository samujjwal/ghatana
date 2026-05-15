#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const CLAIM_PATTERN = /\b(fully implemented|production-ready|complete|enabled|executable|supports)\b/i;
const CLASSIFICATION_PATTERN = /\b(Existing and executable|Existing but partial|Declared only|Target architecture|Anti-pattern)\b/i;

function shouldScan(relativePath) {
  const normalized = normalize(relativePath);
  if (/\/(?:adr|archive)\//i.test(normalized)) {
    return false;
  }
  if (/(PLAN|CHECKLIST|TRACKER|IMPLEMENTATION|ADR)-?.*\.md$/i.test(path.basename(normalized))) {
    return false;
  }
  return (
    normalized === 'docs/architecture/DOMAIN_WORKSTREAM_MAP.md' ||
    /(?:CURRENT_STATE|TRUTH)\.md$/i.test(path.basename(normalized)) ||
    normalized.includes('/truth/')
  );
}

function normalize(filePath) {
  return filePath.replace(/\\/g, '/');
}

function listFiles() {
  try {
    return execFileSync(
      'rg',
      ['--files', 'docs', 'config', '.', '-g', '*.md', '-g', '*.markdown', '-g', '*PLAN*.md', '-g', '*ARCHITECTURE*.md'],
      { cwd: repoRoot, encoding: 'utf8' },
    )
      .split(/\r?\n/)
      .filter(Boolean)
      .map(normalize)
      .filter((file) => !file.startsWith('node_modules/'))
      .filter((file) => shouldScan(file));
  } catch {
    const files = [];
    for (const rootSegment of ['docs', 'config']) {
      const absoluteRoot = path.join(repoRoot, rootSegment);
      if (!existsSync(absoluteRoot)) {
        continue;
      }
      walkDirectory(absoluteRoot, files);
    }
    return files;
  }
}

function walkDirectory(directory, files) {
  for (const entry of readdirSync(directory)) {
    const fullPath = path.join(directory, entry);
    const relativePath = normalize(path.relative(repoRoot, fullPath));
    const stats = statSync(fullPath);
    if (stats.isDirectory()) {
      walkDirectory(fullPath, files);
      continue;
    }
    if (/(\.md|\.markdown)$/i.test(entry) && shouldScan(relativePath)) {
      files.push(relativePath);
    }
  }
}

export function findCurrentStateClaimViolations(files) {
  const violations = [];

  for (const file of files) {
    const lines = file.source.split(/\r?\n/);

    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index];
      if (!CLAIM_PATTERN.test(line)) {
        continue;
      }

      const window = lines.slice(Math.max(0, index - 2), Math.min(lines.length, index + 3)).join(' ');
      if (!CLASSIFICATION_PATTERN.test(window)) {
        violations.push(`${file.path}:${index + 1}: current-state claim '${line.trim()}' is unclassified. Add one of: Existing and executable, Existing but partial, Declared only, Target architecture, Anti-pattern.`);
      }
    }
  }

  return violations;
}

export function checkCurrentStateClaims(options = {}) {
  const files = options.files ?? listFiles().map((file) => ({
    path: file,
    source: readFileSync(path.join(repoRoot, file), 'utf8'),
  }));
  return findCurrentStateClaimViolations(files);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const violations = checkCurrentStateClaims();

  if (violations.length === 0) {
    console.log('OK: current-state claim checks passed.');
    process.exit(0);
  }

  console.error('FAIL: current-state claim checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}