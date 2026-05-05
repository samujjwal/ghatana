#!/usr/bin/env node

import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const violations = [];
const configFiles = [];
const bannedFragments = [
  'platform/typescript/foundation/platform-utils/src/index.ts',
  '../foundation/platform-utils/src/index.ts',
];

function walk(relativeDir) {
  const absoluteDir = path.join(repoRoot, relativeDir);
  for (const entry of readdirSync(absoluteDir)) {
    const absoluteEntry = path.join(absoluteDir, entry);
    const relativeEntry = path.relative(repoRoot, absoluteEntry);
    const stats = statSync(absoluteEntry);
    if (stats.isDirectory()) {
      if (entry === 'node_modules' || entry === 'dist' || entry === '.git') {
        continue;
      }
      walk(relativeEntry);
      continue;
    }

    if (entry === 'vite.config.ts' || entry === 'vitest.config.ts') {
      configFiles.push(relativeEntry);
    }
  }
}

walk('platform');
walk('products');

for (const configFile of configFiles) {
  const source = readFileSync(path.join(repoRoot, configFile), 'utf8');
  for (const bannedFragment of bannedFragments) {
    if (source.includes(bannedFragment)) {
      violations.push(`${configFile} references stale workspace alias path fragment "${bannedFragment}"`);
    }
  }
}

if (violations.length > 0) {
  console.error('Workspace source alias check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Workspace source alias check passed.');
