#!/bin/bash
#
# Detect duplicate workspace package names across package.json files.
# Fails CI when any package name is declared in multiple locations.
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

node - "$ROOT_DIR" <<'NODE'
const fs = require('fs');
const path = require('path');

const root = process.argv[2];
const skipDirs = new Set([
  '.git',
  '.gradle',
  '.pnpm-store',
  'node_modules',
  'dist',
  'build',
  'coverage',
  '.turbo',
  '.next',
  'target',
  '.idea',
  '.vscode'
]);

const packageMap = new Map();

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (skipDirs.has(entry.name)) continue;
      walk(path.join(dir, entry.name));
      continue;
    }

    if (!entry.isFile() || entry.name !== 'package.json') continue;

    const fullPath = path.join(dir, entry.name);
    let data;
    try {
      data = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
    } catch (err) {
      console.error(`Failed to parse ${fullPath}: ${err.message}`);
      process.exit(1);
    }

    if (!data.name || typeof data.name !== 'string') continue;
    const relPath = path.relative(root, fullPath);
    const existing = packageMap.get(data.name) || [];
    existing.push(relPath);
    packageMap.set(data.name, existing);
  }
}

walk(root);

const duplicates = [...packageMap.entries()]
  .filter(([, files]) => files.length > 1)
  .sort(([a], [b]) => a.localeCompare(b));

if (duplicates.length === 0) {
  console.log('No duplicate package names found.');
  process.exit(0);
}

console.error('Duplicate package names detected:');
for (const [pkg, files] of duplicates) {
  console.error(`  ${pkg}`);
  for (const file of files) {
    console.error(`    - ${file}`);
  }
}

process.exit(1);
NODE
