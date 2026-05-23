#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

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

export function extractPathMethodKeys(source) {
  const normalized = source.replace(/\r\n/g, '\n');
  const keys = new Set();
  const methodNames = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options']);

  const trimmed = normalized.trimStart();
  if (trimmed.startsWith('{')) {
    try {
      const parsed = JSON.parse(normalized);
      const paths = parsed?.paths;
      if (paths && typeof paths === 'object') {
        for (const [routePath, operations] of Object.entries(paths)) {
          if (!operations || typeof operations !== 'object') {
            continue;
          }
          for (const [methodName] of Object.entries(operations)) {
            const loweredMethod = String(methodName).toLowerCase();
            if (methodNames.has(loweredMethod)) {
              keys.add(`${loweredMethod.toUpperCase()} ${routePath}`);
            }
          }
        }
      }
      return keys;
    } catch {
      // Fall through to YAML scanning if JSON parse fails.
    }
  }

  let inPathsBlock = false;
  let currentPath = null;

  for (const line of normalized.split('\n')) {
    if (!inPathsBlock) {
      if (/^paths:\s*$/.test(line.trim())) {
        inPathsBlock = true;
      }
      continue;
    }

    if (/^\S/.test(line) && line.trim() !== 'paths:') {
      inPathsBlock = false;
      currentPath = null;
      continue;
    }

    const pathMatch = line.match(/^\s{2}(\/[^\s]+):\s*$/);
    if (pathMatch) {
      currentPath = pathMatch[1];
      continue;
    }

    if (!currentPath) {
      continue;
    }

    const methodMatch = line.match(/^\s{4}(get|post|put|patch|delete|head|options):\s*$/i);
    if (methodMatch) {
      keys.add(`${methodMatch[1].toUpperCase()} ${currentPath}`);
    }
  }

  return keys;
}

export function detectRemovedOperations({ baselineKeys, currentKeys, waivers, specId }) {
  const removed = [...baselineKeys].filter((entry) => !currentKeys.has(entry)).sort();
  const waivedRemoved = removed.filter((entry) => waivers.has(`${specId}:${entry}`));
  const unwaivedRemoved = removed.filter((entry) => !waivers.has(`${specId}:${entry}`));

  return {
    removed,
    waivedRemoved,
    unwaivedRemoved,
  };
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

    const { removed, waivedRemoved, unwaivedRemoved } = detectRemovedOperations({
      baselineKeys,
      currentKeys,
      waivers,
      specId: spec.id,
    });

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

const isMainModule = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMainModule) {
  main();
}
