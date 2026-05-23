#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const schemaPath = path.join(root, 'config/product-family-asset-registry.schema.json');
const registryPath = path.join(root, 'config/product-family-asset-registry.json');

function fail(message) {
  console.error(message);
  process.exit(1);
}

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function main() {
  const schema = readJson(schemaPath);
  const registry = readJson(registryPath);

  if (schema?.type !== 'object') {
    fail('Asset registry schema must declare a root object type');
  }
  if (!Array.isArray(registry?.assets)) {
    fail('Asset registry must contain assets array');
  }

  const ids = new Set();
  for (const asset of registry.assets) {
    if (!asset?.id || !/^[a-z0-9-]+$/.test(asset.id)) {
      fail(`Invalid asset id: ${asset?.id ?? '<missing>'}`);
    }
    if (ids.has(asset.id)) {
      fail(`Duplicate asset id: ${asset.id}`);
    }
    ids.add(asset.id);

    if (!Array.isArray(asset.evidenceRefs) || asset.evidenceRefs.length === 0) {
      fail(`Asset ${asset.id} must include evidenceRefs`);
    }
    if (!['candidate', 'hardened', 'production', 'shared'].includes(asset.status)) {
      fail(`Asset ${asset.id} has invalid status: ${asset.status}`);
    }
    if (!['draft', 'validated', 'release-ready'].includes(asset.maturity)) {
      fail(`Asset ${asset.id} has invalid maturity: ${asset.maturity}`);
    }
  }

  console.log('Product family asset registry validation passed.');
}

main();
