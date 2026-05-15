#!/usr/bin/env node
// Authoritative Source: docs/kernel/PRODUCT_ARTIFACT_CONTRACT.md

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as yaml from 'yaml';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');

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

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadKernelProductConfig(productId, lifecycleConfigPath) {
  const absolutePath = join(repoRoot, lifecycleConfigPath);
  if (!existsSync(absolutePath)) {
    throw new Error(`Product ${productId}: lifecycle config file not found at ${lifecycleConfigPath}`);
  }
  return yaml.parse(readFileSync(absolutePath, 'utf8'));
}

function checkProductArtifactContracts(registry) {
  const errors = [];
  const warnings = [];

  for (const [productId, product] of Object.entries(registry)) {
    const lifecycleStatus = product.lifecycleStatus;
    const enabled = lifecycleStatus === 'enabled' && product.lifecycle?.enabled === true;

    if (!enabled) {
      continue;
    }

    if (typeof product.lifecycleConfigPath !== 'string' || product.lifecycleConfigPath.length === 0) {
      errors.push(`Product ${productId}: enabled lifecycle product must declare lifecycleConfigPath`);
      continue;
    }

    let config;
    try {
      config = loadKernelProductConfig(productId, product.lifecycleConfigPath);
    } catch (error) {
      errors.push(error instanceof Error ? error.message : String(error));
      continue;
    }

    const surfaces = config.surfaces ?? {};
    const artifacts = config.artifacts ?? {};

    if (Object.keys(artifacts).length === 0) {
      errors.push(`Product ${productId}: kernel-product.yaml is missing artifacts declarations`);
      continue;
    }

    for (const [phase, phaseArtifacts] of Object.entries(artifacts)) {
      if (!phaseArtifacts || typeof phaseArtifacts !== 'object') {
        errors.push(`Product ${productId}: artifacts.${phase} must be an object`);
        continue;
      }

      for (const [surfaceId, artifact] of Object.entries(phaseArtifacts)) {
        if (!surfaces[surfaceId]) {
          errors.push(`Product ${productId}: artifacts.${phase}.${surfaceId} does not map to a declared surface`);
          continue;
        }

        if (!artifact || typeof artifact !== 'object') {
          errors.push(`Product ${productId}: artifacts.${phase}.${surfaceId} must be an object`);
          continue;
        }

        if (!artifact.type || !validArtifactTypes.has(artifact.type)) {
          errors.push(`Product ${productId}: artifacts.${phase}.${surfaceId}.type is missing or invalid`);
        }

        if (!artifact.packaging || !validPackagings.has(artifact.packaging)) {
          errors.push(`Product ${productId}: artifacts.${phase}.${surfaceId}.packaging is missing or invalid`);
        }

        if (artifact.required !== undefined && typeof artifact.required !== 'boolean') {
          errors.push(`Product ${productId}: artifacts.${phase}.${surfaceId}.required must be boolean when present`);
        }

        if (artifact.required === true) {
          const requiresPathMapping = artifact.type !== 'container-image';
          if (requiresPathMapping && (!Array.isArray(artifact.paths) || artifact.paths.length === 0)) {
            errors.push(`Product ${productId}: artifacts.${phase}.${surfaceId}.paths are required when artifact is required for non-container artifacts`);
          }
        }
      }
    }

    for (const [surfaceId, surfaceConfig] of Object.entries(surfaces)) {
      const expectedOutputs = surfaceConfig?.expectedOutputs;
      if (!expectedOutputs || typeof expectedOutputs !== 'object') {
        warnings.push(`Product ${productId}: surfaces.${surfaceId} does not define expectedOutputs`);
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
}

try {
  main();
} catch (error) {
  console.error(`Artifact contract check failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
