#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
const exclusionsPath = join(repoRoot, 'config/kernel-lifecycle-exclusions.json');
const packageJsonPath = join(repoRoot, 'package.json');

function loadJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function hasLifecycleScriptForProduct(scripts, productId) {
  return Object.values(scripts).some((scriptValue) => {
    if (typeof scriptValue !== 'string') {
      return false;
    }
    return scriptValue.includes('kernel-product.mjs product') && scriptValue.includes(productId);
  });
}

function main() {
  const registry = loadJson(registryPath).registry ?? {};
  const exclusions = loadJson(exclusionsPath).excludedProducts ?? {};
  const scripts = loadJson(packageJsonPath).scripts ?? {};
  const errors = [];

  for (const productId of Object.keys(exclusions)) {
    const product = registry[productId];
    if (!product) {
      errors.push(`Excluded product ${productId} is missing from canonical registry.`);
      continue;
    }

    if (product.lifecycleStatus === 'enabled') {
      errors.push(`Excluded product ${productId} must not set lifecycleStatus=enabled.`);
    }

    if (product.lifecycle?.enabled === true) {
      errors.push(`Excluded product ${productId} must not set lifecycle.enabled=true.`);
    }

    if (product.lifecycleConfigPath) {
      errors.push(`Excluded product ${productId} must not declare lifecycleConfigPath.`);
    }

    if (hasLifecycleScriptForProduct(scripts, productId)) {
      errors.push(`Excluded product ${productId} must not have root package scripts that invoke kernel-product lifecycle commands.`);
    }
  }

  if (errors.length > 0) {
    console.error('Kernel lifecycle exclusions check failed:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('Kernel lifecycle exclusions are valid');
}

try {
  main();
} catch (error) {
  console.error(`Kernel lifecycle exclusions check failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}