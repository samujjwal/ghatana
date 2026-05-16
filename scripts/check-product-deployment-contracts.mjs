#!/usr/bin/env node
// Authoritative Source: docs/kernel/PRODUCT_DEPLOYMENT_CONTRACT.md

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as yaml from 'yaml';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
const deploymentTargetsPath = join(repoRoot, 'config/deployment-targets.json');

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadDeploymentTargets() {
  return JSON.parse(readFileSync(deploymentTargetsPath, 'utf8')).targets ?? {};
}

function loadKernelProductConfig(productId, lifecycleConfigPath) {
  const absolutePath = join(repoRoot, lifecycleConfigPath);
  if (!existsSync(absolutePath)) {
    throw new Error(`Product ${productId}: lifecycle config file not found at ${lifecycleConfigPath}`);
  }
  return yaml.parse(readFileSync(absolutePath, 'utf8'));
}

function checkProductDeploymentContracts(registry, deploymentTargets) {
  const errors = [];
  const warnings = [];

  for (const [productId, product] of Object.entries(registry)) {
    const enabled = product.lifecycleStatus === 'enabled' && product.lifecycle?.enabled === true;
    if (!enabled) {
      continue;
    }

    if (typeof product.lifecycleConfigPath !== 'string' || product.lifecycleConfigPath.length === 0) {
      errors.push(`Product ${productId}: enabled lifecycle product must declare lifecycleConfigPath`);
      continue;
    }

    const registryTargets = product.deployment?.targets ?? [];
    if (registryTargets.length === 0) {
      errors.push(`Product ${productId}: enabled lifecycle product is missing deployment targets in registry`);
      continue;
    }

    for (const target of registryTargets) {
      if (!deploymentTargets[target]) {
        errors.push(`Product ${productId}: deployment target "${target}" not found`);
      }
    }

    const supportedEnvironments = product.environments?.supported ?? [];
    if (!supportedEnvironments.includes('local')) {
      errors.push(`Product ${productId}: enabled lifecycle product must support local environment in registry`);
    }

    let config;
    try {
      config = loadKernelProductConfig(productId, product.lifecycleConfigPath);
    } catch (error) {
      errors.push(error instanceof Error ? error.message : String(error));
      continue;
    }

    const localDeployment = config.deployment?.local;
    if (!localDeployment || typeof localDeployment !== 'object') {
      errors.push(`Product ${productId}: kernel-product.yaml is missing deployment.local`);
      continue;
    }

    const target = localDeployment.target;
    if (typeof target !== 'string' || target.length === 0) {
      errors.push(`Product ${productId}: deployment.local.target is required`);
    } else if (!registryTargets.includes(target)) {
      errors.push(`Product ${productId}: deployment.local.target ${target} is not listed in registry deployment targets`);
    }

    const composeFile = localDeployment.composeFile;
    if (typeof composeFile !== 'string' || composeFile.length === 0) {
      errors.push(`Product ${productId}: deployment.local.composeFile is required`);
    } else if (!existsSync(join(repoRoot, composeFile))) {
      errors.push(`Product ${productId}: compose file not found at ${composeFile}`);
    }

    const envFile = localDeployment.envFile;
    const requireEnvFile = localDeployment.requireEnvFile !== false;
    if (typeof envFile !== 'string' || envFile.length === 0) {
      warnings.push(`Product ${productId}: deployment.local.envFile is not declared`);
    } else if (requireEnvFile && !existsSync(join(repoRoot, envFile))) {
      warnings.push(`Product ${productId}: env file ${envFile} is not present locally`);
    }

    const envExampleFile = localDeployment.envExampleFile;
    if (typeof envExampleFile !== 'string' || envExampleFile.length === 0) {
      errors.push(`Product ${productId}: deployment.local.envExampleFile is required`);
    } else if (!existsSync(join(repoRoot, envExampleFile))) {
      errors.push(`Product ${productId}: env example file not found at ${envExampleFile}`);
    }

    const healthChecks = localDeployment.healthChecks;
    if (!healthChecks || typeof healthChecks !== 'object') {
      errors.push(`Product ${productId}: deployment.local.healthChecks is required`);
      continue;
    }

    const configuredSurfaces = Object.keys(config.surfaces ?? {});
    for (const surfaceId of configuredSurfaces) {
      if (!healthChecks[surfaceId]) {
        errors.push(`Product ${productId}: deployment.local.healthChecks is missing ${surfaceId}`);
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
}

try {
  main();
} catch (error) {
  console.error(`Deployment contract check failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
