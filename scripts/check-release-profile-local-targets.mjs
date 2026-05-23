#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
const readinessTruthStatuses = new Set(['implemented', 'ready', 'enabled']);

function parseArg(flag) {
  const index = process.argv.indexOf(flag);
  if (index >= 0 && process.argv[index + 1]) {
    return process.argv[index + 1];
  }
  return undefined;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function normalizeTargets(product) {
  return asArray(product?.deployment?.targets)
    .map((target) => String(target).trim().toLowerCase())
    .filter(Boolean);
}

function hasReleaseReadySurface(product) {
  return asArray(product?.surfaces).some((surface) =>
    readinessTruthStatuses.has(String(surface?.implementationStatus ?? '').toLowerCase()),
  );
}

function hasReleaseReadyLifecycle(product) {
  return readinessTruthStatuses.has(String(product?.lifecycleStatus ?? '').toLowerCase());
}

function hasNonLocalTarget(targets) {
  return targets.some((target) => !target.includes('local') && target !== 'compose-local');
}

function resolveProductScope(registry) {
  const provided = parseArg('--products');
  if (!provided) {
    return Object.keys(registry);
  }
  return provided
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function main() {
  const registry = JSON.parse(readFileSync(registryPath, 'utf8')).registry ?? {};
  const scopedProducts = resolveProductScope(registry);
  const violations = [];

  for (const productId of scopedProducts) {
    const product = registry[productId];
    if (!product) {
      violations.push(`Product ${productId} is not present in canonical-product-registry.json`);
      continue;
    }
    if (product.kind !== 'business-product') {
      continue;
    }

    const targets = normalizeTargets(product);
    const releaseCandidateLike = hasReleaseReadySurface(product) || hasReleaseReadyLifecycle(product);
    if (releaseCandidateLike && !hasNonLocalTarget(targets)) {
      violations.push(
        `Product ${productId} declares release-ready status but deployment.targets are local-only (${targets.join(', ') || 'none'})`,
      );
    }
  }

  if (violations.length > 0) {
    console.error('Release profile local-target validation failed:\n');
    for (const violation of violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('Release profile local-target validation passed.');
}

main();
