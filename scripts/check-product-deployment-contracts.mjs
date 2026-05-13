#!/usr/bin/env node

/**
 * Check product deployment contracts
 *
 * Validates that:
 * - Products with lifecycle have deployment definitions
 * - Deployment targets are valid
 * - Environment configurations are present
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
const deploymentTargetsPath = join(repoRoot, 'config/deployment/deployment-targets.json');
const environmentsPath = join(repoRoot, 'config/environments');

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadDeploymentTargets() {
  return JSON.parse(readFileSync(deploymentTargetsPath, 'utf8')).targets;
}

function loadEnvironment(envName) {
  const envPath = join(environmentsPath, `${envName}.json`);
  if (!existsSync(envPath)) {
    return null;
  }
  return JSON.parse(readFileSync(envPath, 'utf8'));
}

function checkProductDeploymentContracts(registry, deploymentTargets) {
  const errors = [];
  const warnings = [];

  for (const [productId, product] of Object.entries(registry)) {
    if (!product.deployment) {
      continue;
    }

    // Check deployment target exists
    if (product.deployment.target) {
      if (!deploymentTargets[product.deployment.target]) {
        errors.push(`Product ${productId}: deployment target "${product.deployment.target}" not found`);
      }
    }

    // Check environment references
    if (product.environments && Array.isArray(product.environments)) {
      for (const envName of product.environments) {
        const envConfig = loadEnvironment(envName);
        if (!envConfig) {
          errors.push(`Product ${productId}: environment "${envName}" not found`);
        } else {
          // Validate environment has required fields
          if (!envConfig.name) {
            errors.push(`Product ${productId}: environment ${envName} missing name`);
          }
          if (!envConfig.type) {
            errors.push(`Product ${productId}: environment ${envName} missing type`);
          }
        }
      }
    }

    // Check deployment adapter
    if (product.deployment.adapter) {
      const validAdapters = new Set(['compose-local', 'kubernetes', 'helm', 'terraform']);
      if (!validAdapters.has(product.deployment.adapter)) {
        errors.push(`Product ${productId}: deployment adapter "${product.deployment.adapter}" is not valid`);
      }
    }
  }

  return { errors, warnings };
}

function main() {
  const registry = loadRegistry();
  const deploymentTargets = loadDeploymentTargets();
  const { errors, warnings } = checkProductDeploymentContracts(registry, deploymentTargets);

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

  console.log('All product deployment contracts are valid');
  process.exit(0);
}

try {
  main();
} catch (error) {
  console.error(`Deployment contract check failed: ${error.message}`);
  process.exit(1);
}
