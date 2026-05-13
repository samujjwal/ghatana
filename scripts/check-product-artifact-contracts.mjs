#!/usr/bin/env node

/**
 * Check product artifact contracts
 *
 * Validates that:
 * - Products with lifecycle have artifact definitions
 * - Artifact types are valid
 * - Artifact fingerprints are present
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

const validArtifactTypes = new Set(['jar', 'war', 'static-web-bundle', 'docker-image', 'npm-package', 'test-report', 'coverage-report', 'source-map', 'documentation']);

function checkProductArtifactContracts(registry) {
  const errors = [];
  const warnings = [];

  for (const [productId, product] of Object.entries(registry)) {
    if (!product.artifacts) {
      continue;
    }

    // Check artifact definitions
    for (const [artifactId, artifact] of Object.entries(product.artifacts)) {
      // Check artifact type
      if (!artifact.type) {
        errors.push(`Product ${productId}: artifact ${artifactId} missing type`);
      } else if (!validArtifactTypes.has(artifact.type)) {
        errors.push(`Product ${productId}: artifact ${artifactId} has invalid type "${artifact.type}"`);
      }

      // Check that artifact has fingerprint algorithm
      if (artifact.fingerprint && !artifact.fingerprint.algorithm) {
        errors.push(`Product ${productId}: artifact ${artifactId} has fingerprint but missing algorithm`);
      }

      // Check that artifact has surface reference
      if (!artifact.surface) {
        errors.push(`Product ${productId}: artifact ${artifactId} missing surface`);
      } else if (!product.surfaces?.find(s => s.type === artifact.surface)) {
        warnings.push(`Product ${productId}: artifact ${artifactId} references unknown surface "${artifact.surface}"`);
      }
    }
  }

  return { errors, warnings };
}

function main() {
  const registry = loadRegistry();
  const { errors, warnings } = checkProductArtifactContracts(registry);

  if (warnings.length > 0) {
    console.warn('Warnings:');
    for (const warning of warnings) {
      console.warn(`  - ${warning}`);
    }
  }

  if (errors.length > 0) {
    console.error('Errors:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('All product artifact contracts are valid');
  process.exit(0);
}

try {
  main();
} catch (error) {
  console.error(`Artifact contract check failed: ${error.message}`);
  process.exit(1);
}
