#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const repoRoot = resolve(process.cwd());
const phrMain = join(repoRoot, 'products/phr/src/main/java');
const unsafeTagPattern = /"(?:patient|tenant|principal|facility|user|record)[_-]?id"\s*,/i;
const telemetryCallPattern = /\.(?:incrementCounter|recordMetric|recordGauge|recordHistogram|startTimer)\s*\(/;
const violations = [];

function walk(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(path);
      continue;
    }
    if (entry.isFile() && entry.name.endsWith('.java')) {
      scan(path);
    }
  }
}

function scan(filePath) {
  const lines = readFileSync(filePath, 'utf8').split('\n');
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (!telemetryCallPattern.test(line) && !unsafeTagPattern.test(line)) {
      continue;
    }
    const window = lines.slice(index, Math.min(lines.length, index + 8)).join('\n');
    if (telemetryCallPattern.test(window) && unsafeTagPattern.test(window)) {
      violations.push(`${relative(repoRoot, filePath)}:${index + 1}`);
    }
  }
}

if (!existsSync(phrMain)) {
  console.error(`PHR source directory not found: ${phrMain}`);
  process.exit(1);
}

walk(phrMain);

if (violations.length > 0) {
  console.error('PHR safe telemetry check failed. Unsafe identifier tag names in telemetry calls:');
  for (const violation of violations) {
    console.error(`  - ${violation}`);
  }
  process.exit(1);
}

console.log('PHR safe telemetry check passed.');
