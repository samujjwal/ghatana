#!/usr/bin/env node

/**
 * Check product lifecycle contracts
 *
 * Validates that products with lifecycle enabled have:
 * - Valid lifecycle profile references
 * - Required lifecycle configuration files
 * - Valid lifecycle phase definitions
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
const lifecycleProfilesPath = join(repoRoot, 'config/product-lifecycle-profiles.json');

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadLifecycleProfiles() {
  const data = JSON.parse(readFileSync(lifecycleProfilesPath, 'utf8'));
  return data.profiles || {};
}

function checkProductLifecycleContracts(registry, lifecycleProfiles) {
  const errors = [];
  const warnings = [];

  for (const [productId, product] of Object.entries(registry)) {
    if (!product.lifecycleProfile) {
      continue;
    }

    // Check that lifecycle profile exists
    if (!lifecycleProfiles[product.lifecycleProfile]) {
      errors.push(`Product ${productId}: lifecycle profile "${product.lifecycleProfile}" not found in registry`);
    }

    // Check that lifecycle config path exists
    if (product.lifecycleConfigPath) {
      const configPath = join(repoRoot, product.lifecycleConfigPath);
      if (!existsSync(configPath)) {
        errors.push(`Product ${productId}: lifecycle config file not found at ${product.lifecycleConfigPath}`);
      }
    }

    // Check that lifecycle is enabled
    if (!product.lifecycle?.enabled) {
      warnings.push(`Product ${productId}: lifecycle profile defined but lifecycle.enabled is false`);
    }

    // Check that lifecycle has required fields
    if (product.lifecycle) {
      if (!product.lifecycle.phases || Object.keys(product.lifecycle.phases).length === 0) {
        errors.push(`Product ${productId}: lifecycle.phases is empty or missing`);
      }

      if (!product.lifecycle.toolchain || Object.keys(product.lifecycle.toolchain).length === 0) {
        errors.push(`Product ${productId}: lifecycle.toolchain is empty or missing`);
      }
    }
  }

  return { errors, warnings };
}

function main() {
  const registry = loadRegistry();
  const lifecycleProfiles = loadLifecycleProfiles();
  const { errors, warnings } = checkProductLifecycleContracts(registry, lifecycleProfiles);

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

  console.log('All product lifecycle contracts are valid');
  process.exit(0);
}

try {
  main();
} catch (error) {
  console.error(`Lifecycle contract check failed: ${error.message}`);
  process.exit(1);
}
