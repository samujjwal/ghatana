#!/usr/bin/env node

import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = new URL('..', import.meta.url).pathname;
const srcRoot = join(root, 'src');
const sourceExtensions = new Set(['.ts', '.tsx']);
const violations = [];

function walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'dist' || entry.name === 'node_modules' || entry.name === 'coverage') {
        continue;
      }
      walk(path);
      continue;
    }

    if (![...sourceExtensions].some((extension) => entry.name.endsWith(extension))) {
      continue;
    }

    lintFile(path);
  }
}

function lintFile(path) {
  const source = readFileSync(path, 'utf8');
  const displayPath = relative(root, path);

  assertNoMatch(
    displayPath,
    source,
    /from\s+['"](?:\.\.\/)*products\//,
    'Studio must not import product internals; use public platform contracts instead.',
  );
  assertNoMatch(
    displayPath,
    source,
    /from\s+['"]@(?:yappc|data-cloud|finance|phr|flashit)\//,
    'Studio must not import product package internals.',
  );
  assertNoMatch(
    displayPath,
    source,
    /\bfetch\s*\(/,
    'Studio routes must use typed API clients instead of raw fetch.',
  );

  if (
    !displayPath.includes('__tests__') &&
    !displayPath.endsWith('vite-env.d.ts') &&
    displayPath !== 'src/logging/studioLogger.ts'
  ) {
    assertNoMatch(
      displayPath,
      source,
      /\b(console\.log|console\.warn|console\.error)\s*\(/,
      'Use studioLogger for observable Studio diagnostics.',
    );
  }
}

function assertNoMatch(displayPath, source, pattern, message) {
  const match = pattern.exec(source);
  if (!match) {
    return;
  }

  const line = source.slice(0, match.index).split('\n').length;
  violations.push(`${displayPath}:${line} ${message}`);
}

walk(srcRoot);

if (violations.length > 0) {
  console.error('Studio lint failed:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Studio lint passed');
