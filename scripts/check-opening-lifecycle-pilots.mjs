#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const REQUIRED_OPENING_PILOTS = ['digital-marketing', 'phr'];
const REQUIRED_DISABLED_PRODUCTS = ['finance', 'flashit'];
const REQUIRED_PLATFORM_PROVIDERS = ['data-cloud', 'yappc'];

function readJson(root, relativePath) {
  return JSON.parse(readFileSync(path.join(root, relativePath), 'utf8'));
}

function pathExists(root, relativePath) {
  return existsSync(path.join(root, relativePath));
}

function sorted(value) {
  return [...value].sort();
}

function sameMembers(actual, expected) {
  return JSON.stringify(sorted(actual)) === JSON.stringify(sorted(expected));
}

export function validateOpeningLifecyclePilots(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const releasePlan = options.releasePlan ?? readJson(root, 'config/platform-release-plan.json');
  const registry = options.registry ?? readJson(root, 'config/canonical-product-registry.json').registry;
  const exists = options.pathExists ?? ((relativePath) => pathExists(root, relativePath));
  const errors = [];

  if (releasePlan.schemaVersion !== '1.0.0') {
    errors.push('platform-release-plan schemaVersion must be 1.0.0');
  }
  if (!sameMembers(releasePlan.openingLifecyclePilots ?? [], REQUIRED_OPENING_PILOTS)) {
    errors.push(`openingLifecyclePilots must be exactly: ${REQUIRED_OPENING_PILOTS.join(', ')}`);
  }
  if (!sameMembers(releasePlan.disabledUntilReady ?? [], REQUIRED_DISABLED_PRODUCTS)) {
    errors.push(`disabledUntilReady must be exactly: ${REQUIRED_DISABLED_PRODUCTS.join(', ')}`);
  }
  if (!sameMembers(releasePlan.platformProviderValidators ?? [], REQUIRED_PLATFORM_PROVIDERS)) {
    errors.push(`platformProviderValidators must be exactly: ${REQUIRED_PLATFORM_PROVIDERS.join(', ')}`);
  }

  const enabledLifecycleProducts = Object.values(registry)
    .filter((product) => product.lifecycleStatus === 'enabled' || product.lifecycle?.enabled === true || product.lifecycleExecutionAllowed === true)
    .map((product) => product.id);
  if (!sameMembers(enabledLifecycleProducts, REQUIRED_OPENING_PILOTS)) {
    errors.push(`enabled lifecycle products must be exactly ${REQUIRED_OPENING_PILOTS.join(', ')}, got ${sorted(enabledLifecycleProducts).join(', ')}`);
  }

  for (const productId of REQUIRED_OPENING_PILOTS) {
    const product = registry[productId];
    if (!product) {
      errors.push(`opening pilot missing from registry: ${productId}`);
      continue;
    }
    if (product.lifecycleStatus !== 'enabled' || product.lifecycle?.enabled !== true || product.lifecycleExecutionAllowed !== true) {
      errors.push(`${productId} must have lifecycleStatus=enabled, lifecycle.enabled=true, and lifecycleExecutionAllowed=true`);
    }
    if (!exists(product.lifecycleConfigPath ?? '')) {
      errors.push(`${productId} lifecycleConfigPath missing: ${product.lifecycleConfigPath}`);
    }
  }

  for (const productId of REQUIRED_DISABLED_PRODUCTS) {
    const product = registry[productId];
    if (!product) {
      errors.push(`disabled product missing from registry: ${productId}`);
      continue;
    }
    if (product.lifecycleStatus === 'enabled' || product.lifecycle?.enabled === true || product.lifecycleExecutionAllowed === true) {
      errors.push(`${productId} must remain disabled or fail-closed`);
    }
  }

  for (const productId of REQUIRED_PLATFORM_PROVIDERS) {
    const product = registry[productId];
    if (!product) {
      errors.push(`platform provider missing from registry: ${productId}`);
      continue;
    }
    if (product.kind !== 'platform-provider') {
      errors.push(`${productId} must remain a platform-provider validator`);
    }
    if (product.lifecycleStatus === 'enabled' || product.lifecycle?.enabled === true || product.lifecycleExecutionAllowed === true) {
      errors.push(`${productId} must not be an opening lifecycle product`);
    }
  }

  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const errors = validateOpeningLifecyclePilots();
  if (errors.length === 0) {
    console.log('Opening lifecycle pilot check passed');
    process.exit(0);
  }

  console.error(`Opening lifecycle pilot check FAILED (${errors.length} error(s)):`);
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}
