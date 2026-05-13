#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const registry = JSON.parse(
  readFileSync(path.join(repoRoot, 'config/canonical-product-registry.json'), 'utf8'),
).registry;
const ciMatrix = JSON.parse(
  readFileSync(path.join(repoRoot, 'config/generated/ci-matrix.json'), 'utf8'),
);

const requiredKinds = new Set([
  'platform-provider',
  'business-product',
  'shared-service',
  'domain-pack',
  'demo/example',
]);
const productGateKinds = new Set(['business-product']);
const violations = [];

function registryKindEnumValues() {
  const schema = JSON.parse(
    readFileSync(path.join(repoRoot, 'config/canonical-product-registry-schema.json'), 'utf8'),
  );
  return new Set(schema?.definitions?.Product?.properties?.kind?.enum ?? []);
}

function activeBusinessProductIds() {
  return Object.entries(registry)
    .filter(([, entry]) => entry.kind === 'business-product')
    .filter(([, entry]) => entry.metadata?.status === 'active')
    .filter(([, entry]) => entry.conformance?.manifest === true)
    .map(([productId]) => productId)
    .sort();
}

const schemaKinds = registryKindEnumValues();
for (const kind of requiredKinds) {
  if (!schemaKinds.has(kind)) {
    violations.push(`canonical registry schema must allow ${kind} entries`);
  }
}

for (const [productId, entry] of Object.entries(registry)) {
  if (!requiredKinds.has(entry.kind)) {
    violations.push(`${productId}: unknown kind ${entry.kind}`);
  }

  if (entry.kind !== 'business-product' && entry.conformance?.manifest === true) {
    violations.push(`${productId}: non-business registry entry must not opt into product manifest conformance`);
  }

  if (entry.kind === 'platform-provider') {
    const productOnlyGates = Object.entries(entry.conformance ?? {})
      .filter(([key, value]) => key !== 'bridgeAdapters' && value === true);
    if (productOnlyGates.length > 0) {
      violations.push(
        `${productId}: platform-provider must not declare product conformance gates (${productOnlyGates.map(([key]) => key).join(', ')})`,
      );
    }
  }

  if (entry.kind === 'demo/example' && entry.ci?.enabled === true) {
    violations.push(`${productId}: demo/example entries must not participate in product CI matrices`);
  }
}

const expectedProductGates = activeBusinessProductIds();
const ciProducts = [...(ciMatrix.products ?? [])].sort();

for (const productId of expectedProductGates) {
  if (!ciProducts.includes(productId)) {
    violations.push(`config/generated/ci-matrix.json missing active business product ${productId}`);
  }
}

for (const productId of ciProducts) {
  const entry = registry[productId];
  if (!entry) {
    violations.push(`config/generated/ci-matrix.json references unknown product ${productId}`);
    continue;
  }
  if (!productGateKinds.has(entry.kind) && entry.kind !== 'platform-provider' && entry.kind !== 'shared-service') {
    violations.push(`config/generated/ci-matrix.json must not include ${entry.kind} entry ${productId}`);
  }
}

if (violations.length > 0) {
  console.error('Product kind classification check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Product kind classification check passed.');
