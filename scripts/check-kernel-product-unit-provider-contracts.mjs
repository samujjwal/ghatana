#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function readText(relativePath) {
  return readFileSync(join(repoRoot, relativePath), 'utf8');
}

function readJson(relativePath) {
  return JSON.parse(readText(relativePath));
}

function requireFile(relativePath, errors) {
  if (!existsSync(join(repoRoot, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
  }
}

function requireIncludes(text, needle, label, errors) {
  if (!text.includes(needle)) {
    errors.push(`${label} must include ${needle}`);
  }
}

function surfaceTypes(product) {
  return new Set((product.surfaces ?? []).map((surface) => surface.type));
}

function requireSurfaces(registry, productId, expectedSurfaces, errors) {
  const product = registry[productId];
  if (!product) {
    errors.push(`Registry is missing product "${productId}"`);
    return;
  }

  const actual = surfaceTypes(product);
  for (const expected of expectedSurfaces) {
    if (!actual.has(expected)) {
      errors.push(`Product "${productId}" is missing surface "${expected}"`);
    }
  }
}

function main() {
  const errors = [];

  requireFile('platform/typescript/kernel-product-contracts/src/product-unit/ProductUnit.ts', errors);
  requireFile('platform/typescript/kernel-product-contracts/src/provider/RegistryProvider.ts', errors);
  requireFile('platform/typescript/kernel-product-contracts/src/provider/ProviderRef.ts', errors);
  requireFile('platform/typescript/kernel-providers/package.json', errors);
  requireFile('platform/typescript/kernel-providers/src/registry/GhatanaFileRegistryProvider.ts', errors);
  requireFile('platform/typescript/kernel-providers/src/registry/__tests__/GhatanaFileRegistryProvider.test.ts', errors);

  const contractsIndex = readText('platform/typescript/kernel-product-contracts/src/index.ts');
  requireIncludes(contractsIndex, 'ProductUnit', 'ProductUnit contracts root export', errors);
  requireIncludes(contractsIndex, 'RegistryProvider', 'Provider contracts root export', errors);
  requireIncludes(contractsIndex, 'ProviderRef', 'ProviderRef contracts root export', errors);

  const productUnit = readText('platform/typescript/kernel-product-contracts/src/product-unit/ProductUnit.ts');
  if (productUnit.includes('export interface ProviderRef')) {
    errors.push('ProductUnit.ts must not define a local ProviderRef');
  }
  requireIncludes(productUnit, '../provider/ProviderRef', 'ProductUnit canonical ProviderRef import', errors);

  const providerSource = readText('platform/typescript/kernel-providers/src/registry/GhatanaFileRegistryProvider.ts');
  requireIncludes(providerSource, 'implements RegistryProvider', 'GhatanaFileRegistryProvider', errors);
  requireIncludes(providerSource, 'validateRegistry', 'GhatanaFileRegistryProvider', errors);
  requireIncludes(providerSource, 'clearCache', 'GhatanaFileRegistryProvider', errors);

  const rootPackage = readJson('package.json');
  const buildScript = rootPackage.scripts?.['build:kernel-lifecycle-platform'] ?? '';
  if (!buildScript.includes('platform/typescript/kernel-providers build')) {
    errors.push('build:kernel-lifecycle-platform must include kernel-providers');
  }

  const providerPackage = readJson('platform/typescript/kernel-providers/package.json');
  if (providerPackage.name !== '@ghatana/kernel-providers') {
    errors.push('kernel-providers package name must be @ghatana/kernel-providers');
  }
  const providerDependencies = Object.keys(providerPackage.dependencies ?? {});
  if (providerDependencies.some((dependency) => dependency !== '@ghatana/kernel-product-contracts')) {
    errors.push('kernel-providers must depend only on @ghatana/kernel-product-contracts');
  }

  const registry = readJson('config/canonical-product-registry.json').registry;
  requireSurfaces(registry, 'digital-marketing', ['backend-api', 'web'], errors);
  requireSurfaces(registry, 'finance', ['backend-api', 'operator', 'portal', 'sdk'], errors);
  requireSurfaces(registry, 'flashit', ['backend-api', 'web'], errors);
  const flashitSurfaces = surfaceTypes(registry.flashit ?? {});
  if (!flashitSurfaces.has('mobile') && !flashitSurfaces.has('mobile-ios') && !flashitSurfaces.has('mobile-android')) {
    errors.push('Product "flashit" must expose mobile or concrete mobile surfaces');
  }

  if (registry.yappc?.kind !== 'platform-provider') {
    errors.push('YAPPC must map as product kind platform-provider');
  }

  for (const [productId, product] of Object.entries(registry)) {
    if (product.lifecycleStatus === 'disabled' && product.lifecycle?.enabled === true) {
      errors.push(`Disabled product "${productId}" must not claim lifecycle.enabled=true`);
    }
  }

  if (errors.length > 0) {
    console.error('ProductUnit provider contract validation failed:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('ProductUnit provider contract validation passed.');
}

main();
