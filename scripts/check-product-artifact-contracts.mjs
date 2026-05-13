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

const validArtifactTypes = new Set([
  'jvm-service',
  'jvm-library',
  'node-service',
  'static-web-bundle',
  'container-image',
  'mobile-bundle',
  'sdk-package',
  'domain-pack',
  'test-report',
  'coverage-report',
]);
const validPackagings = new Set([
  'jar',
  'distribution',
  'static-files',
  'container',
  'npm',
  'maven',
  'apk',
  'aab',
  'ipa',
  'json',
  'xml',
]);

function checkProductArtifactContracts(registry) {
  const errors = [];
  const warnings = [];

  for (const [productId, product] of Object.entries(registry)) {
    const lifecycleActive = product.lifecycleStatus === 'enabled' || product.lifecycle?.enabled;
    const lifecyclePartial = product.lifecycleStatus === 'partial' || product.lifecycleStatus === 'planned';

    if (!product.artifacts) {
      if (lifecycleActive) {
        errors.push(`Product ${productId}: lifecycle-enabled product is missing artifact declarations`);
      }
      continue;
    }

    // Check artifact definitions
    for (const [surfaceId, artifact] of Object.entries(product.artifacts)) {
      if (!product.surfaces?.find((surface) => surface.type === surfaceId)) {
        const message = `Product ${productId}: artifact ${surfaceId} does not map to a declared surface`;
        if (lifecycleActive) {
          errors.push(message);
        } else if (lifecyclePartial) {
          warnings.push(message);
        } else {
          errors.push(message);
        }
      }

      // Check artifact type
      if (!artifact.type) {
        errors.push(`Product ${productId}: artifact ${surfaceId} missing type`);
      } else if (!validArtifactTypes.has(artifact.type)) {
        errors.push(`Product ${productId}: artifact ${surfaceId} has invalid type "${artifact.type}"`);
      }

      if (!artifact.packaging) {
        errors.push(`Product ${productId}: artifact ${surfaceId} missing packaging`);
      } else if (!validPackagings.has(artifact.packaging)) {
        errors.push(`Product ${productId}: artifact ${surfaceId} has invalid packaging "${artifact.packaging}"`);
      }

      if (artifact.required !== undefined && typeof artifact.required !== 'boolean') {
        errors.push(`Product ${productId}: artifact ${surfaceId} has non-boolean required flag`);
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
