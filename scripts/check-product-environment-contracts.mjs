#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path, { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const defaultRepoRoot = resolve(__dirname, '..');
const registryPath = join(defaultRepoRoot, 'config/canonical-product-registry.json');
const registrySchemaPath = join(defaultRepoRoot, 'config/canonical-product-registry-schema.json');

/**
 * Check product environment contracts
 */
function checkProductEnvironmentContracts() {
  const errors = [];

  // Load registry and schema
  const registry = JSON.parse(readFileSync(registryPath, 'utf8'));
  const schema = JSON.parse(readFileSync(registrySchemaPath, 'utf8'));

  // Check each product
  for (const [productId, product] of Object.entries(registry.registry)) {
    // Skip inactive products
    if (product.metadata?.status !== 'active') {
      continue;
    }

    // Check if lifecycle is enabled
    if (product.lifecycle?.enabled) {
      // Check for lifecycle config path
      if (!product.lifecycleConfigPath) {
        errors.push(`${productId}: lifecycle enabled but missing lifecycleConfigPath`);
      }

      // Check for environment configuration
      if (!product.environments) {
        errors.push(`${productId}: lifecycle enabled but missing environments configuration`);
      } else {
        // Check supported environments
        if (!product.environments.supported || product.environments.supported.length === 0) {
          errors.push(`${productId}: environments configuration missing supported environments`);
        }

        // Check that supported environments are valid
        const validEnvironments = ['local', 'dev', 'staging', 'prod'];
        for (const env of product.environments.supported) {
          if (!validEnvironments.includes(env)) {
            errors.push(`${productId}: invalid environment '${env}' in supported environments`);
          }
        }
      }

      // Check for deployment configuration if product is deployable
      if (product.deployment) {
        // Check for deployment targets
        if (!product.deployment.targets || product.deployment.targets.length === 0) {
          errors.push(`${productId}: deployment enabled but missing deployment targets`);
        }

        // Check for default environment
        if (!product.deployment.defaultEnvironment) {
          errors.push(`${productId}: deployment enabled but missing defaultEnvironment`);
        }

        // Check for health checks
        if (!product.deployment.healthChecks || product.deployment.healthChecks.length === 0) {
          errors.push(`${productId}: deployment enabled but missing health checks`);
        }
      }
    }
  }

  if (errors.length > 0) {
    console.error('Product environment contract check failed:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('Product environment contract check passed');
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    checkProductEnvironmentContracts();
  } catch (error) {
    console.error(`Product environment contract check failed: ${error.message}`);
    process.exit(1);
  }
}
