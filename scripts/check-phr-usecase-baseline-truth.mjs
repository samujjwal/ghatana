#!/usr/bin/env node

/**
 * Validates that the PHR use-case baseline describes the live Kernel-native API truth.
 *
 * The baseline is documentation-grade configuration, not evidence generation. This
 * check is intentionally read-only and fails when implemented/partial use cases
 * point at legacy non-versioned APIs or hidden route families.
 */

import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.argv[2] ?? process.cwd());
const baselinePath = resolve(root, 'products/phr/config/phr-usecase-baseline.json');
const routeContractPath = resolve(root, 'products/phr/config/phr-route-contract.json');

function readJson(path, label) {
  if (!existsSync(path)) {
    throw new Error(`${label} not found: ${path}`);
  }
  return JSON.parse(readFileSync(path, 'utf8'));
}

function parseApi(api, useCaseId) {
  const [method, rawPath] = String(api).trim().split(/\s+/, 2);
  if (!method || !rawPath) {
    return { error: `${useCaseId}: invalid backend API declaration '${api}'` };
  }
  const path = rawPath.split('?')[0].replace(/\/+$/, '') || '/';
  return { method, path, rawPath };
}

function apiFamily(path) {
  const parts = path.split('/').filter(Boolean);
  if (parts.length < 3 || parts[0] !== 'api' || parts[1] !== 'v1') {
    return null;
  }
  return `/api/v1/${parts[2]}`;
}

function hiddenFamilyPrefix(apiEndpoint) {
  if (typeof apiEndpoint !== 'string' || apiEndpoint.trim() === '') {
    return null;
  }
  return apiFamily(apiEndpoint.split('?')[0].replace(/\/+$/, ''));
}

let errors = 0;
const baseline = readJson(baselinePath, 'PHR use-case baseline');
const routeContract = readJson(routeContractPath, 'PHR route contract');

if (!Array.isArray(baseline.usecases)) {
  console.error('[phr-usecase-baseline-truth] FAIL: baseline.usecases must be an array');
  process.exit(1);
}
if (!Array.isArray(routeContract.routes)) {
  console.error('[phr-usecase-baseline-truth] FAIL: route contract routes must be an array');
  process.exit(1);
}

const hiddenApiFamilies = new Set(
  routeContract.routes
    .filter((route) => route.stability === 'hidden')
    .map((route) => hiddenFamilyPrefix(route.apiEndpoint))
    .filter((prefix) => prefix !== null),
);

for (const useCase of baseline.usecases) {
  const useCaseId = useCase.id ?? 'unknown-usecase';
  const status = useCase.status ?? 'missing';
  const backendApis = Array.isArray(useCase.backendApis) ? useCase.backendApis : [];

  for (const api of backendApis) {
    const parsed = parseApi(api, useCaseId);
    if (parsed.error) {
      console.error(`[phr-usecase-baseline-truth] ${parsed.error}`);
      errors += 1;
      continue;
    }

    if (!parsed.path.startsWith('/api/v1/')) {
      console.error(
        `[phr-usecase-baseline-truth] ${useCaseId}: backend API '${api}' must use canonical /api/v1 path`,
      );
      errors += 1;
    }

    const family = apiFamily(parsed.path);
    if (status !== 'deferred' && family !== null && hiddenApiFamilies.has(family)) {
      console.error(
        `[phr-usecase-baseline-truth] ${useCaseId}: status '${status}' cannot claim hidden API family ${family}`,
      );
      errors += 1;
    }
  }
}

if (errors > 0) {
  console.error(`[phr-usecase-baseline-truth] FAIL: ${errors} violation(s) found`);
  process.exit(1);
}

console.log(
  `[phr-usecase-baseline-truth] PASS: ${baseline.usecases.length} use cases use canonical API truth`,
);
