#!/usr/bin/env node

/**
 * Lifecycle Registry Config Drift Checker
 *
 * Detects drift between the canonical product registry
 * (config/canonical-product-registry.json) and kernel-product.yaml files
 * present under products/ directories.
 *
 * Reports:
 * - Products registered in the registry but missing kernel-product.yaml
 * - Products with kernel-product.yaml but not registered in the registry
 * - Products registered but whose productId in kernel-product.yaml mismatches the registry key
 *
 * Exit code 0 = no drift detected
 * Exit code 1 = drift found (review required)
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'fs';
import { join, basename } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = join(__filename, '..');

const REPO_ROOT = join(__dirname, '../../..');
const REGISTRY_PATH = join(REPO_ROOT, 'config/canonical-product-registry.json');
const PRODUCTS_DIR = join(REPO_ROOT, 'products');

let driftFound = false;

function warn(msg) {
  console.warn(`  ⚠  ${msg}`);
  driftFound = true;
}

function pass(msg) {
  console.log(`  ✓  ${msg}`);
}

function info(msg) {
  console.log(`  ℹ  ${msg}`);
}

/**
 * Extract the productId field from a kernel-product.yaml file.
 * Returns null if the file cannot be read or the field is missing.
 */
function extractProductId(yamlContent) {
  const match = yamlContent.match(/^productId:\s*(.+)$/m);
  return match ? match[1].trim() : null;
}

/**
 * Recursively find all kernel-product.yaml files under a directory.
 */
function findKernelProductYamls(dir, results = []) {
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return results;
  }
  for (const entry of entries) {
    if (entry.startsWith('.') || entry === 'node_modules' || entry === 'build') continue;
    const fullPath = join(dir, entry);
    let stat;
    try {
      stat = statSync(fullPath);
    } catch {
      continue;
    }
    if (stat.isDirectory()) {
      findKernelProductYamls(fullPath, results);
    } else if (entry === 'kernel-product.yaml') {
      results.push(fullPath);
    }
  }
  return results;
}

// === Load registry ===

console.log('\nLifecycle Registry Config Drift Checker');
console.log('========================================\n');

if (!existsSync(REGISTRY_PATH)) {
  console.error(`❌ Cannot find canonical-product-registry.json at: ${REGISTRY_PATH}`);
  process.exit(1);
}

let registryData;
try {
  registryData = JSON.parse(readFileSync(REGISTRY_PATH, 'utf-8'));
} catch (err) {
  console.error(`❌ Failed to parse canonical-product-registry.json: ${err.message}`);
  process.exit(1);
}

const registry = registryData.registry ?? {};
const registeredProductIds = new Set(Object.keys(registry));

// === Find all kernel-product.yaml files ===

const yamlFiles = findKernelProductYamls(PRODUCTS_DIR);
const yamlByProductId = new Map(); // productId → file path

for (const yamlPath of yamlFiles) {
  const content = readFileSync(yamlPath, 'utf-8');
  const productId = extractProductId(content);
  if (productId) {
    yamlByProductId.set(productId, yamlPath);
  } else {
    warn(`kernel-product.yaml at ${yamlPath.replace(REPO_ROOT + '/', '')} has no productId field`);
  }
}

// === Check 1: Registry products missing kernel-product.yaml ===

console.log('1. Registry products missing kernel-product.yaml:');
let registryMissing = 0;
for (const productId of registeredProductIds) {
  if (!yamlByProductId.has(productId)) {
    const entry = registry[productId];
    const productType = entry?.type ?? 'unknown';
    // Only flag product-type entries (not infra/platform registrations)
    if (productType === 'product') {
      warn(`"${productId}" is in registry (type: product) but has no kernel-product.yaml`);
      registryMissing++;
    } else {
      info(`"${productId}" is in registry (type: ${productType}) — kernel-product.yaml optional`);
    }
  }
}
if (registryMissing === 0) {
  pass('All registered products of type "product" have kernel-product.yaml');
}

// === Check 2: kernel-product.yaml files without registry entry ===

console.log('\n2. kernel-product.yaml files without registry entry:');
let yamlMissing = 0;
for (const [productId, yamlPath] of yamlByProductId) {
  if (!registeredProductIds.has(productId)) {
    warn(
      `"${productId}" has kernel-product.yaml (${yamlPath.replace(REPO_ROOT + '/', '')}) but is NOT in canonical-product-registry.json`
    );
    yamlMissing++;
  }
}
if (yamlMissing === 0) {
  pass('All kernel-product.yaml files reference registered products');
}

// === Check 3: productId in YAML matches the registry key ===

console.log('\n3. productId consistency (YAML field vs registry key):');
let idMismatch = 0;
for (const [productId, yamlPath] of yamlByProductId) {
  if (registeredProductIds.has(productId)) {
    pass(`"${productId}" — productId matches registry key`);
  }
  // Mismatches already surfaced in Check 2
}

// === Summary ===

console.log('\n========================================');
if (!driftFound) {
  console.log('✅ No registry/config drift detected.');
  process.exit(0);
} else {
  console.error('\n❌ Drift detected — review warnings above and align the registry and kernel-product.yaml files.');
  process.exit(1);
}
