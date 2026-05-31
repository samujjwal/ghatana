#!/usr/bin/env node

import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { createChecker, repoRoot } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-014 no PHR-specific knowledge',
  evidencePath: '.kernel/evidence/yappc/no-phr-knowledge.json',
});

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

function listFiles(startPath) {
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

const violations = [];
for (const root of roots) {
  for (const filePath of listFiles(root)) {
    const relativePath = path.relative(repoRoot, filePath).replaceAll(path.sep, '/');
    if (!isTextFile(filePath)) {
      continue;
    }
    if (domainPattern.test(relativePath)) {
      violations.push({ file: relativePath, line: 0, match: relativePath });
    }
    domainPattern.lastIndex = 0;

    const lines = readFileSync(filePath, 'utf8').split(/\r?\n/);
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
  }
}

checker.record('YAPPC has no PHR-specific domain identifiers', violations.length === 0, {
  violationCount: violations.length,
  violations: violations.slice(0, 50),
});

checker.finish({
  scannedRoots: roots,
  scannedAt: new Date().toISOString(),
});
