#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

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

function extractField(content, fieldName) {
  const match = content.match(new RegExp(`^\\s*${fieldName}:\\s*(.+)$`, 'm'));
  return match ? match[1].trim() : null;
}

function checkProductDeploymentContracts(registry, deploymentTargets) {
  const errors = [];
  const warnings = [];

  for (const [productId, product] of Object.entries(registry)) {
    if (product.lifecycleStatus !== 'enabled') {
      continue;
    }

    const targets = product.deployment?.targets ?? [];
    if (targets.length === 0) {
      errors.push(`Product ${productId}: enabled lifecycle product is missing deployment targets`);
      continue;
    }

    for (const target of targets) {
      if (!deploymentTargets[target]) {
        errors.push(`Product ${productId}: deployment target "${target}" not found`);
      }
    }

    const supportedEnvironments = product.environments?.supported ?? [];
    if (!supportedEnvironments.includes('local')) {
      errors.push(`Product ${productId}: enabled lifecycle product must support the local environment`);
    }

    const localLifecyclePath = join(repoRoot, 'products', productId, 'lifecycle.local.yaml');
    if (!existsSync(localLifecyclePath)) {
      errors.push(`Product ${productId}: local lifecycle overlay not found at products/${productId}/lifecycle.local.yaml`);
      continue;
    }

    const localLifecycleContent = readFileSync(localLifecyclePath, 'utf8');
    const composeFile = extractField(localLifecycleContent, 'composeFile');
    const envFile = extractField(localLifecycleContent, 'envFile');
    const envExampleFile = extractField(localLifecycleContent, 'envExampleFile');

    if (!composeFile) {
      errors.push(`Product ${productId}: local lifecycle overlay is missing composeFile`);
    } else if (!existsSync(join(repoRoot, composeFile))) {
      errors.push(`Product ${productId}: compose file not found at ${composeFile}`);
    }

    if (!envFile) {
      errors.push(`Product ${productId}: local lifecycle overlay is missing envFile`);
    } else if (!existsSync(join(repoRoot, envFile))) {
      warnings.push(`Product ${productId}: env file ${envFile} is not present locally`);
    }

    if (!envExampleFile) {
      errors.push(`Product ${productId}: local lifecycle overlay is missing envExampleFile`);
    } else if (!existsSync(join(repoRoot, envExampleFile))) {
      errors.push(`Product ${productId}: env example file not found at ${envExampleFile}`);
    }

    const healthChecksPath = join(repoRoot, 'products', productId, 'deploy', 'health-checks.json');
    if (!existsSync(healthChecksPath)) {
      errors.push(`Product ${productId}: health checks file not found at products/${productId}/deploy/health-checks.json`);
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
