#!/usr/bin/env node

/**
 * Generate Precomputed Route Manifests
 * 
 * Generates precomputed route manifests based on product entitlements.
 * This replaces local route transformation with precomputed generated manifests.
 * 
 * Usage: node scripts/generate-precomputed-route-manifests.mjs
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '..');

function loadCanonicalProductRegistry() {
  const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    console.warn('Canonical product registry not found');
    return null;
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

function generatePrecomputedManifest(productId, entitlements) {
  const routeManifest = loadProductRouteManifest(productId);
  if (!routeManifest) {
    console.warn(`No route manifest found for ${productId}`);
    return null;
  }
  
  const precomputed = {
    productId,
    generatedAt: new Date().toISOString(),
    entitlements: entitlements.map(entitlement => ({
      role: entitlement.role,
      persona: entitlement.persona,
      tier: entitlement.tier,
      routes: routeManifest.routes.filter(route => {
        if (route.minimumRole && !isRoleSufficient(entitlement.role, route.minimumRole)) {
          return false;
        }
        if (entitlement.persona && route.personas && !route.personas.includes(entitlement.persona)) {
          return false;
        }
        if (entitlement.tier && route.tiers && !route.tiers.includes(entitlement.tier)) {
          return false;
        }
        return true;
      }),
    })),
  };
  
  return precomputed;
}

function isRoleSufficient(currentRole, minimumRole) {
  // Simplified role comparison - in real implementation, use role order
  return currentRole === minimumRole || currentRole === 'admin';
}

function writePrecomputedManifest(productId, precomputed) {
  const outputPath = join(repoRoot, 'config', 'generated-route-manifests', `${productId}-precomputed.json`);
  writeFileSync(outputPath, JSON.stringify(precomputed, null, 2) + '\n');
  console.log(`Generated precomputed manifest for ${productId} at ${outputPath}`);
}

function main() {
  console.log('=== Generating Precomputed Route Manifests ===\n');
  
  const registry = loadCanonicalProductRegistry();
  if (!registry) {
    console.error('Failed to load canonical product registry');
    process.exit(1);
  }
  
  const products = Object.keys(registry.registry);
  let generatedCount = 0;
  
  for (const productId of products) {
    const product = registry.registry[productId];
    
    // Generate sample entitlements for each role
    const sampleEntitlements = [
      { role: 'admin', persona: null, tier: null },
      { role: 'operator', persona: null, tier: null },
      { role: 'viewer', persona: null, tier: null },
    ];
    
    const precomputed = generatePrecomputedManifest(productId, sampleEntitlements);
    if (precomputed) {
      writePrecomputedManifest(productId, precomputed);
      generatedCount++;
    }
  }
  
  console.log(`\nGenerated ${generatedCount} precomputed route manifests`);
}

main();
