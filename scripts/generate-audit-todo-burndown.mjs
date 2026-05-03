#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const PRODUCT_ROOTS = [
  { name: 'data-cloud', path: 'products/data-cloud' },
  { name: 'aep', path: 'products/aep' },
  { name: 'yappc', path: 'products/yappc' },
];

const EXCLUDED_DIRS = new Set([
  '.git',
  '.gradle',
  '.idea',
  '.next',
  '.turbo',
  '.vscode',
  'build',
  'dist',
  'out',
  'target',
  'coverage',
  'node_modules',
  '.venv',
  'venv',
  'site-packages',
  '.tools',
]);

const INCLUDE_EXTENSIONS = new Set([
  '.java',
  '.kt',
  '.kts',
  '.ts',
  '.tsx',
  '.js',
  '.jsx',
  '.mjs',
  '.cjs',
  '.py',
  '.rs',
  '.go',
  '.sh',
  '.bash',
  '.md',
  '.yaml',
  '.yml',
]);

const MARKER_PATTERN = /\b(TODO|FIXME|HACK|XXX)\b/g;

function toPosix(p) {
  return p.split(path.sep).join('/');
}

function shouldExcludeDir(name) {
  if (EXCLUDED_DIRS.has(name)) {
    return true;
  }
  return name.startsWith('.pnpm');
}

function walkFiles(dirPath, out) {
  if (!fs.existsSync(dirPath)) {
    return;
  }

  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      if (shouldExcludeDir(entry.name)) {
        continue;
      }
      walkFiles(full, out);
      continue;
    }

    if (entry.isFile() && INCLUDE_EXTENSIONS.has(path.extname(full))) {
      out.push(full);
    }
  }
}

function countMarkersInFile(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  const lines = content.split(/\r?\n/);
  const hits = [];

  for (let i = 0; i < lines.length; i += 1) {
    MARKER_PATTERN.lastIndex = 0;
    const line = lines[i];
    const matches = line.match(MARKER_PATTERN);
    if (matches) {
      hits.push({
        line: i + 1,
        count: matches.length,
        text: line.trim(),
      });
    }
  }

  return hits;
}

const results = {
  generatedAt: new Date().toISOString(),
  products: {},
  totals: {
    filesWithMarkers: 0,
    markers: 0,
  },
};

for (const product of PRODUCT_ROOTS) {
  const absRoot = path.join(repoRoot, product.path);
  const files = [];
  walkFiles(absRoot, files);

  const fileHits = [];
  let markerCount = 0;

  for (const filePath of files) {
    const hits = countMarkersInFile(filePath);
    if (hits.length === 0) {
      continue;
    }

    const totalInFile = hits.reduce((sum, h) => sum + h.count, 0);
    markerCount += totalInFile;

    fileHits.push({
      file: toPosix(path.relative(repoRoot, filePath)),
      markerCount: totalInFile,
      lines: hits,
    });
  }

  results.products[product.name] = {
    root: product.path,
    filesWithMarkers: fileHits.length,
    markerCount,
    files: fileHits.sort((a, b) => b.markerCount - a.markerCount),
  };

  results.totals.filesWithMarkers += fileHits.length;
  results.totals.markers += markerCount;
}

const reportDir = path.join(repoRoot, 'build', 'reports', 'audit');
fs.mkdirSync(reportDir, { recursive: true });

const jsonPath = path.join(reportDir, 'todo-burndown.json');
fs.writeFileSync(jsonPath, `${JSON.stringify(results, null, 2)}\n`, 'utf8');

const markdownLines = [];
markdownLines.push('# Audit TODO Burndown');
markdownLines.push('');
markdownLines.push(`Generated at: ${results.generatedAt}`);
markdownLines.push('');
markdownLines.push('## Summary');
markdownLines.push('');
markdownLines.push(`- Files with markers: ${results.totals.filesWithMarkers}`);
markdownLines.push(`- Total markers: ${results.totals.markers}`);
markdownLines.push('');

for (const product of PRODUCT_ROOTS) {
  const p = results.products[product.name];
  markdownLines.push(`## ${product.name}`);
  markdownLines.push('');
  markdownLines.push(`- Root: ${p.root}`);
  markdownLines.push(`- Files with markers: ${p.filesWithMarkers}`);
  markdownLines.push(`- Marker count: ${p.markerCount}`);
  markdownLines.push('');

  const topFiles = p.files.slice(0, 50);
  if (topFiles.length === 0) {
    markdownLines.push('No markers found.');
    markdownLines.push('');
    continue;
  }

  markdownLines.push('| File | Markers |');
  markdownLines.push('|---|---:|');
  for (const file of topFiles) {
    markdownLines.push(`| ${file.file} | ${file.markerCount} |`);
  }
  markdownLines.push('');
}

const mdPath = path.join(reportDir, 'todo-burndown.md');
fs.writeFileSync(mdPath, `${markdownLines.join('\n')}\n`, 'utf8');

console.log(`Wrote ${toPosix(path.relative(repoRoot, jsonPath))}`);
console.log(`Wrote ${toPosix(path.relative(repoRoot, mdPath))}`);
