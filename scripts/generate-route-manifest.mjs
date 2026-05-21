#!/usr/bin/env node

/**
 * Generate Route Manifest
 * 
 * Generates route manifests for platform-provider gates integration.
 * This script validates route manifests against provider contracts and runtime-truth.
 * 
 * Usage: node scripts/generate-route-manifest.mjs [--check]
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '..');

const CHECK_MODE = process.argv.includes('--check');

function loadCanonicalProductRegistry() {
  const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    console.error('Canonical product registry not found');
    process.exit(1);
  }
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

function loadProductRouteManifest(productId) {
  const registry = loadCanonicalProductRegistry();
  if (!registry || !registry.registry[productId]) {
    return null;
  }
  
  const product = registry.registry[productId];
  const manifestPath = join(repoRoot, product.manifestPath);
  
  if (!existsSync(manifestPath)) {
    return null;
  }
  
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  return manifest;
}

function validateRouteManifest(productId, manifest) {
  const errors = [];
  const warnings = [];

  // Validate manifest structure
  if (!manifest.routes || !Array.isArray(manifest.routes)) {
    errors.push('Manifest must have a routes array');
    return { valid: false, errors, warnings };
  }

  // Validate each route
  for (const route of manifest.routes) {
    if (!route.path) {
      errors.push('Route must have a path');
    }
    if (!route.label) {
      errors.push('Route must have a label');
    }
    if (!route.capabilities || !Array.isArray(route.capabilities)) {
      warnings.push(`Route ${route.path} should have capabilities array`);
    }
  }

  return { valid: errors.length === 0, errors, warnings };
}

function generateRouteManifestForProvider(productId) {
  const routeManifest = loadProductRouteManifest(productId);
  if (!routeManifest) {
    console.warn(`No route manifest found for ${productId}`);
    return null;
  }
  
  const validation = validateRouteManifest(productId, routeManifest);
  if (!validation.valid) {
    console.error(`Route manifest validation failed for ${productId}:`);
    validation.errors.forEach(error => console.error(`  - ${error}`));
    if (CHECK_MODE) {
      process.exit(1);
    }
  }

  if (validation.warnings.length > 0) {
    console.warn(`Route manifest warnings for ${productId}:`);
    validation.warnings.forEach(warning => console.warn(`  - ${warning}`));
  }

  // Generate provider-gate manifest
  const providerGateManifest = {
    productId,
    generatedAt: new Date().toISOString(),
    routes: routeManifest.routes.map(route => ({
      path: route.path,
      label: route.label,
      capabilities: route.capabilities || [],
      providerGates: route.providerGates || [],
      runtimeTruth: route.runtimeTruth || null,
    })),
  };

  return providerGateManifest;
}

function writeProviderGateManifest(productId, manifest) {
  const outputPath = join(repoRoot, 'config', 'provider-gate-manifests', `${productId}-provider-gate.json`);
  
  if (CHECK_MODE) {
    if (!existsSync(outputPath)) {
      console.error(`Provider gate manifest not found for ${productId} at ${outputPath}`);
      process.exit(1);
    }
    
    const existing = JSON.parse(readFileSync(outputPath, 'utf8'));
    const existingHash = JSON.stringify(existing);
    const newHash = JSON.stringify(manifest);
    
    if (existingHash !== newHash) {
      console.error(`Provider gate manifest drift detected for ${productId}`);
      console.error(`Run: node scripts/generate-route-manifest.mjs`);
      process.exit(1);
    }
    
    console.log(`Provider gate manifest validated for ${productId}`);
  } else {
    writeFileSync(outputPath, JSON.stringify(manifest, null, 2) + '\n');
    console.log(`Generated provider gate manifest for ${productId} at ${outputPath}`);
  }
}

function main() {
  console.log(CHECK_MODE ? '=== Checking Route Manifests ===' : '=== Generating Route Manifests ===\n');
  
  const registry = loadCanonicalProductRegistry();
  const products = Object.keys(registry.registry);
  let processedCount = 0;
  
  for (const productId of products) {
    const product = registry.registry[productId];
    
    // Skip products without route manifests
    if (!product.manifestPath) {
      continue;
    }
    
    const manifest = generateRouteManifestForProvider(productId);
    if (manifest) {
      writeProviderGateManifest(productId, manifest);
      processedCount++;
    }
  }
  
  console.log(`\n${CHECK_MODE ? 'Checked' : 'Generated'} ${processedCount} route manifests`);
}

main();
