#!/usr/bin/env node
/**
 * DMOS production no-fake-data guard.
 *
 * @doc.type tooling
 * @doc.purpose Fail CI when production DMOS UI/API code contains fake KPI or canned analytics data
 * @doc.layer infrastructure
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));

const scanRoots = [
  'products/digital-marketing/ui/src',
  'products/digital-marketing/dm-api/src/main/java',
  'products/digital-marketing/dm-application/src/main/java',
].map((path) => join(repoRoot, path));

const sourceExtensions = new Set(['.ts', '.tsx', '.java']);
const excludedPathFragments = [
  '/__tests__/',
  '/test/',
  '/tests/',
  '/types/api-generated.ts/',
  '/generated/',
];
const excludedFilePatterns = [
  /\.test\.[jt]sx?$/,
  /\.spec\.[jt]sx?$/,
  /Test\.java$/,
  /IT\.java$/,
];

const bannedLinePatterns = [
  {
    id: 'FAKE_KPI_LABEL',
    pattern: /\b(fake|mock|sample|dummy|canned|hardcoded)\b.{0,48}\b(kpi|metric|metrics|analytics|dashboard|chart|performance|spend|revenue|lead|conversion)\b/i,
    description: 'Fake/sample/canned KPI or analytics label in production code',
  },
  {
    id: 'FAKE_KPI_SUFFIX',
    pattern: /\b(kpi|metric|metrics|analytics|dashboard|chart|performance|spend|revenue|lead|conversion)\b.{0,48}\b(fake|mock|sample|dummy|canned|hardcoded)\b/i,
    description: 'Production metric code is marked fake/sample/canned',
  },
  {
    id: 'STATIC_PERFORMANCE_FIXTURE',
    pattern: /\b(Q[1-4]\s+(Acquisition|Retargeting|Performance)|Holiday Retargeting|sample marketing data|static performance metrics)\b/i,
    description: 'Static marketing fixture data in production code',
  },
];

const staticMetricAssignmentPattern =
  /\b(?:const|let|var)\s+[A-Za-z0-9_$]*(?:Kpis?|Metrics?|Analytics|Dashboard|Performance|Spend|Revenue|Leads?|Conversions?)[A-Za-z0-9_$]*\s*=\s*(?:\[|\{)/;

function walk(dir) {
  if (!existsSync(dir)) {
    return [];
  }

  const files = [];
  for (const entry of readdirSync(dir)) {
    const fullPath = join(dir, entry);
    const rel = `/${relative(repoRoot, fullPath).replace(/\\/g, '/')}`;
    if (excludedPathFragments.some((fragment) => rel.includes(fragment))) {
      continue;
    }

    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      files.push(...walk(fullPath));
    } else if (sourceExtensions.has(extname(fullPath)) && !excludedFilePatterns.some((pattern) => pattern.test(fullPath))) {
      files.push(fullPath);
    }
  }
  return files;
}

const violations = [];

for (const root of scanRoots) {
  for (const file of walk(root)) {
    const rel = relative(repoRoot, file).replace(/\\/g, '/');
    const lines = readFileSync(file, 'utf8').split('\n');
    lines.forEach((line, index) => {
      const trimmed = line.trim();
      if (trimmed.includes('DMOS_NO_FAKE_DATA_ALLOW')) {
        return;
      }

      for (const { id, pattern, description } of bannedLinePatterns) {
        if (pattern.test(trimmed)) {
          violations.push({ file: rel, line: index + 1, id, description, text: trimmed });
        }
      }

      if (staticMetricAssignmentPattern.test(trimmed)) {
        violations.push({
          file: rel,
          line: index + 1,
          id: 'STATIC_METRIC_ASSIGNMENT',
          description: 'Static KPI/metric collection assigned in production code; use backend summary/API data instead',
          text: trimmed,
        });
      }
    });
  }
}

if (violations.length > 0) {
  console.error('DMOS no-fake-data guard failed:');
  for (const violation of violations) {
    console.error(`- ${violation.file}:${violation.line} [${violation.id}] ${violation.description}`);
    console.error(`  ${violation.text.slice(0, 160)}`);
  }
  process.exit(1);
}

console.log('DMOS no-fake-data guard passed.');
