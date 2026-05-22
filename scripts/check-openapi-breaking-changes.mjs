#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'openapi-breaking-changes.json');
const waiversPath = path.join(repoRoot, 'config/openapi-breaking-change-waivers.json');

const specs = [
  {
    id: 'data-cloud',
    current: 'products/data-cloud/contracts/openapi/data-cloud.yaml',
    baseline: 'release-evidence/openapi-baseline/data-cloud.yaml',
  },
  {
    id: 'aep',
    current: 'products/data-cloud/contracts/openapi/aep.yaml',
    baseline: 'release-evidence/openapi-baseline/aep.yaml',
  },
  {
    id: 'digital-marketing',
    current: 'products/digital-marketing/dm-api/src/main/resources/openapi.json',
    baseline: 'release-evidence/openapi-baseline/dmos-openapi.json',
  },
  {
    id: 'flashit',
    current: 'products/flashit/backend/gateway/openapi.yaml',
    baseline: 'release-evidence/openapi-baseline/flashit-openapi.yaml',
  },
];

function extractPathMethodKeys(source) {
  const normalized = source.replace(/\r\n/g, '\n');
  const keys = new Set();

  const yamlRegex = /^\s{2}(\/[^:\n]+):\n([\s\S]*?)(?=^\s{2}\/|^\S|\Z)/gm;
  for (const match of normalized.matchAll(yamlRegex)) {
    const routePath = match[1].trim();
    const block = match[2];
    for (const method of ['get', 'post', 'put', 'patch', 'delete', 'head', 'options']) {
      if (new RegExp(`^\\s{4}${method}:`, 'm').test(block)) {
        keys.add(`${method.toUpperCase()} ${routePath}`);
      }
    }
  }

  const jsonRegex = /"(\/[^"\n]+)"\s*:\s*\{([\s\S]*?)(?=\n\s*"\/|\n\s*\}|\Z)/g;
  for (const match of normalized.matchAll(jsonRegex)) {
    const routePath = match[1].trim();
    const block = match[2];
    for (const method of ['get', 'post', 'put', 'patch', 'delete', 'head', 'options']) {
      if (new RegExp(`"${method}"\\s*:`).test(block)) {
        keys.add(`${method.toUpperCase()} ${routePath}`);
      }
    }
  }

  return keys;
}

function loadWaivers() {
  if (!existsSync(waiversPath)) {
    return new Set();
  }
  const waivers = JSON.parse(readFileSync(waiversPath, 'utf8'));
  return new Set((waivers.allowedBreakingChanges ?? []).map((entry) => String(entry)));
}

export function runOpenApiBreakingChangeCheck() {
  const violations = [];
  const details = [];
  const waivers = loadWaivers();

  for (const spec of specs) {
    const currentPath = path.join(repoRoot, spec.current);
    const baselinePath = path.join(repoRoot, spec.baseline);

    if (!existsSync(currentPath)) {
      violations.push(`Missing current OpenAPI spec for ${spec.id}: ${spec.current}`);
      continue;
    }

    if (!existsSync(baselinePath)) {
      violations.push(`Missing OpenAPI baseline for ${spec.id}: ${spec.baseline}`);
      continue;
    }

    const currentKeys = extractPathMethodKeys(readFileSync(currentPath, 'utf8'));
    const baselineKeys = extractPathMethodKeys(readFileSync(baselinePath, 'utf8'));

    const removed = [...baselineKeys].filter((entry) => !currentKeys.has(entry)).sort();
    const waivedRemoved = removed.filter((entry) => waivers.has(`${spec.id}:${entry}`));
    const unwaivedRemoved = removed.filter((entry) => !waivers.has(`${spec.id}:${entry}`));

    if (unwaivedRemoved.length > 0) {
      violations.push(
        `Breaking OpenAPI removal detected for ${spec.id}: ${unwaivedRemoved.join(', ')}`,
      );
    }

    details.push({
      id: spec.id,
      baselinePath: spec.baseline,
      currentPath: spec.current,
      removedCount: removed.length,
      waivedRemoved,
      unwaivedRemoved,
    });
  }

  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(
    evidencePath,
    `${JSON.stringify({
      generatedAt: new Date().toISOString(),
      status: violations.length === 0 ? 'passed' : 'failed',
      details,
      violations,
    }, null, 2)}\n`,
    'utf8',
  );

  return {
    pass: violations.length === 0,
    violations,
  };
}

function main() {
  const result = runOpenApiBreakingChangeCheck();
  if (!result.pass) {
    console.error('OpenAPI breaking-change check failed:');
    for (const violation of result.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('OpenAPI breaking-change check passed.');
}

main();
