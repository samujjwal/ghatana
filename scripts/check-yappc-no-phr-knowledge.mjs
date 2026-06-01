#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createChecker, repoRoot } from './lib/yappc-release-check-utils.mjs';

const thisFile = fileURLToPath(import.meta.url);

const roots = [
  'products/yappc',
  'scripts/check-yappc-product-contract-roundtrip.mjs',
];
const ignoredSegments = new Set([
  '.gradle',
  '.turbo',
  'build',
  'coverage',
  'dist',
  'node_modules',
  'target',
]);
const textExtensions = new Set([
  '.css',
  '.html',
  '.java',
  '.js',
  '.json',
  '.jsx',
  '.kt',
  '.kts',
  '.md',
  '.mjs',
  '.proto',
  '.scss',
  '.ts',
  '.tsx',
  '.txt',
  '.yaml',
  '.yml',
]);
const domainPattern = /\bPHR\b|\bPhr[A-Za-z0-9_]*\b|(?<![A-Za-z0-9_])phr(?![A-Za-z0-9_])/g;

function isIgnored(filePath) {
  return filePath.split(path.sep).some((segment) => ignoredSegments.has(segment));
}

function isTextFile(filePath) {
  return textExtensions.has(path.extname(filePath));
}

function listTrackedFiles(startPath) {
  try {
    const output = execFileSync('git', ['ls-files', '--', startPath], {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    });
    return output
      .split(/\r?\n/)
      .filter(Boolean)
      .map((file) => path.resolve(repoRoot, file));
  } catch {
    return [];
  }
}

function listFiles(startPath) {
  const trackedFiles = listTrackedFiles(startPath);
  if (trackedFiles.length > 0) {
    return trackedFiles;
  }

  const absolute = path.resolve(repoRoot, startPath);
  const stat = statSync(absolute);
  if (stat.isFile()) {
    return [absolute];
  }

  const result = [];
  for (const entry of readdirSync(absolute)) {
    const child = path.join(absolute, entry);
    if (isIgnored(path.relative(repoRoot, child))) {
      continue;
    }
    const childStat = statSync(child);
    if (childStat.isDirectory()) {
      result.push(...listFiles(path.relative(repoRoot, child)));
    } else if (childStat.isFile()) {
      result.push(child);
    }
  }
  return result;
}

export function findYappcPhrKnowledgeViolations(scanRoots = roots) {
  const violations = [];
  for (const root of scanRoots) {
    for (const filePath of listFiles(root)) {
      const relativePath = path.relative(repoRoot, filePath).replaceAll(path.sep, '/');
      if (!isTextFile(filePath)) {
        continue;
      }
      violations.push(...findPhrDomainIdentifierViolations(relativePath, readFileSync(filePath, 'utf8')));
    }
  }
  return violations;
}

export function findPhrDomainIdentifierViolations(relativePath, source) {
  const violations = [];
  if (domainPattern.test(relativePath)) {
    violations.push({ file: relativePath, line: 0, match: relativePath });
  }
  domainPattern.lastIndex = 0;

  const lines = source.split(/\r?\n/);
  lines.forEach((line, index) => {
    domainPattern.lastIndex = 0;
    let match = domainPattern.exec(line);
    while (match != null) {
      violations.push({
        file: relativePath,
        line: index + 1,
        match: match[0],
      });
      match = domainPattern.exec(line);
    }
  });

  return violations;
}

function main() {
  const args = process.argv.slice(2);
  const checker = createChecker({
    checkId: 'YAPPC-014 no PHR-specific knowledge',
    evidencePath: args.includes('--no-evidence') ? null : '.kernel/evidence/yappc/no-phr-knowledge.json',
  });
  const violations = findYappcPhrKnowledgeViolations();
  checker.record('YAPPC has no PHR-specific domain identifiers', violations.length === 0, {
    violationCount: violations.length,
    violations: violations.slice(0, 50),
  });

  checker.finish({
    scannedRoots: roots,
    scannedAt: new Date().toISOString(),
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === thisFile) {
  main();
}
