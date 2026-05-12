#!/usr/bin/env node

/**
 * Generate Observability Flow Checks from Product Manifests
 * 
 * Generates observability flow check configurations based on product manifests.
 * This ensures each product has the required observability instrumentation.
 * 
 * Usage: node scripts/generate-observability-flow-checks.mjs [--check]
 */

import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

// Parse command line arguments
const args = process.argv.slice(2);
const checkMode = args.includes('--check');

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function loadCanonicalRegistry() {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    throw new Error('Canonical product registry not found');
  }
  return JSON.parse(readFileSync(registryPath, 'utf8'));
}

async function loadManifests() {
  const registry = loadCanonicalRegistry();
  const manifests = [];
  
  for (const [productId, product] of Object.entries(registry.registry)) {
    if (product.manifestPath && product.manifestFormat === 'json') {
      const manifestPath = path.join(repoRoot, product.manifestPath);
      if (existsSync(manifestPath)) {
        const content = readFileSync(manifestPath, 'utf8');
        const manifest = JSON.parse(content);
        manifests.push({ productId, manifest });
      }
    }
  }
  
  return manifests;
}

function generateObservabilityChecks(manifests) {
  const checks = {
    version: '1.0.0',
    generated: new Date().toISOString(),
    products: {}
  };
  
  for (const { productId, manifest } of manifests) {
    const pack = manifest.pack || manifest;
    const runtimeServices = pack.runtimeServices || [];
    const dataSensitivity = pack.dataSensitivity;
    
    const productChecks = {
      requiredFlows: [],
      requiredMetrics: [],
      requiredTraces: [],
      requiredLogs: [],
      dataSensitivity: dataSensitivity
    };
    
    // Core observability requirements
    productChecks.requiredFlows.push('request-latency', 'error-rate', 'throughput');
    productChecks.requiredMetrics.push('cpu', 'memory', 'disk', 'network');
    productChecks.requiredTraces.push('http-request', 'database-query', 'external-call');
    productChecks.requiredLogs.push('info', 'warn', 'error');
    
    // Runtime service specific checks
    for (const service of runtimeServices) {
      productChecks.requiredFlows.push(`${service}-health`);
      productChecks.requiredMetrics.push(`${service}-connections`);
    }
    
    // Data sensitivity specific checks
    if (dataSensitivity === 'regulated-health' || dataSensitivity === 'regulated-finance') {
      productChecks.requiredLogs.push('audit', 'access-control');
      productChecks.requiredTraces.push('data-access', 'policy-evaluation');
    }
    
    checks.products[productId] = productChecks;
  }
  
  return checks;
}

async function main() {
  console.log('=== Observability Flow Check Generator ===\n');
  
  try {
    const manifests = await loadManifests();
    console.log(`Loaded ${manifests.length} product manifests\n`);
    
    const checks = generateObservabilityChecks(manifests);
    
    const outputPath = path.join(repoRoot, 'config/generated/observability-flow-checks.json');
    
    if (checkMode) {
      const existingChecks = existsSync(outputPath) 
        ? readJson('config/generated/observability-flow-checks.json') 
        : null;
      
      if (!existingChecks) {
        console.error('✗ No existing observability flow checks found');
        process.exit(1);
      }
      
      // Compare checks (simplified comparison)
      const currentProductCount = Object.keys(checks.products).length;
      const existingProductCount = Object.keys(existingChecks.products).length;
      
      if (currentProductCount !== existingProductCount) {
        console.error(`✗ Product count mismatch: current=${currentProductCount}, existing=${existingProductCount}`);
        console.error('Run: node scripts/generate-observability-flow-checks.mjs');
        process.exit(1);
      }
      
      console.log('✓ Observability flow checks are up to date');
    } else {
      writeFileSync(outputPath, JSON.stringify(checks, null, 2) + '\n');
      console.log(`Generated observability flow checks at ${outputPath}`);
      console.log(`  - ${Object.keys(checks.products).length} products`);
    }
    
    process.exit(0);
  } catch (error) {
    console.error('✗ Observability flow check generation failed:');
    console.error(error.message);
    process.exit(1);
  }
}

main();
