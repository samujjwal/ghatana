#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
const lifecycleProfilesPath = join(repoRoot, 'config/product-lifecycle-profiles.json');
const toolchainRegistryPath = join(repoRoot, 'config/toolchain-adapter-registry.json');
const lifecycleExclusionsPath = join(repoRoot, 'config/kernel-lifecycle-exclusions.json');

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadLifecycleProfiles() {
  return JSON.parse(readFileSync(lifecycleProfilesPath, 'utf8')).profiles ?? {};
}

function loadToolchainRegistry() {
  return JSON.parse(readFileSync(toolchainRegistryPath, 'utf8')).adapters ?? {};
}

function loadLifecycleExclusions() {
  return JSON.parse(readFileSync(lifecycleExclusionsPath, 'utf8')).excludedProducts ?? {};
}

function checkProductLifecycleContracts(registry, lifecycleProfiles, toolchains, excludedProducts) {
  const errors = [];
  const warnings = [];

  for (const [productId, product] of Object.entries(registry)) {
    const isExcluded = Boolean(excludedProducts[productId]);
    if (isExcluded) {
      if (product.lifecycleStatus === 'enabled') {
        errors.push(`Excluded product ${productId}: lifecycleStatus must not be enabled`);
      }
      if (product.lifecycle?.enabled === true) {
        errors.push(`Excluded product ${productId}: lifecycle.enabled must not be true`);
      }
      if (product.lifecycleConfigPath) {
        errors.push(`Excluded product ${productId}: lifecycleConfigPath must not be declared`);
      }
      continue;
    }

    if (!product.lifecycleProfile) {
      continue;
    }

    if (!lifecycleProfiles[product.lifecycleProfile]) {
      errors.push(`Product ${productId}: lifecycle profile "${product.lifecycleProfile}" not found in registry`);
    }

    const lifecycleStatus = product.lifecycleStatus ?? (product.lifecycle?.enabled ? 'enabled' : 'partial');
    if (!['disabled', 'planned', 'partial', 'enabled'].includes(lifecycleStatus)) {
      errors.push(`Product ${productId}: lifecycleStatus "${lifecycleStatus}" is invalid`);
    }

    if (lifecycleStatus === 'enabled') {
      if (!product.lifecycleConfigPath) {
        errors.push(`Product ${productId}: enabled lifecycle product must declare lifecycleConfigPath`);
        continue;
      }

      const configPath = join(repoRoot, product.lifecycleConfigPath);
      if (!existsSync(configPath)) {
        errors.push(`Product ${productId}: lifecycle config file not found at ${product.lifecycleConfigPath}`);
        continue;
      }

      if (!product.lifecycle?.enabled) {
        errors.push(`Product ${productId}: lifecycleStatus is enabled but lifecycle.enabled is not true`);
      }

      const lifecycleProfile = lifecycleProfiles[product.lifecycleProfile];
      if (lifecycleProfile && lifecycleProfile.safeForDefault !== true) {
        errors.push(`Product ${productId}: enabled lifecycle product must use a safeForDefault lifecycle profile`);
      }

      if (!product.artifacts || Object.keys(product.artifacts).length === 0) {
        errors.push(`Product ${productId}: enabled lifecycle product is missing artifact declarations`);
      }

      if (!product.deployment?.targets?.length) {
        errors.push(`Product ${productId}: enabled lifecycle product is missing deployment targets`);
      }

      const enabledAdapters = new Set([
        ...Object.values(product.lifecycle?.toolchain ?? {}),
        ...Object.values(product.toolchain?.adapters ?? {}),
      ]);

      for (const adapterId of enabledAdapters) {
        if (!toolchains[adapterId]) {
          errors.push(`Product ${productId}: lifecycle references unknown adapter ${adapterId}`);
          continue;
        }
        if (toolchains[adapterId].safeForDefault !== true) {
          errors.push(`Product ${productId}: enabled lifecycle product cannot use unsafe adapter ${adapterId}`);
        }
      }

      for (const planPhase of ['validate', 'test', 'build']) {
        try {
          execFileSync(
            process.execPath,
            [join(repoRoot, 'scripts', 'kernel-product.mjs'), 'product', 'plan', productId, planPhase, '--json'],
            { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' },
          );
        } catch (error) {
          errors.push(`Product ${productId}: ${planPhase} plan generation failed: ${error instanceof Error ? error.message : String(error)}`);
        }
      }
    } else {
      if (product.lifecycle?.enabled) {
        warnings.push(`Product ${productId}: lifecycle.enabled is true while lifecycleStatus is ${lifecycleStatus}`);
      }
      if (product.lifecycleConfigPath) {
        const configPath = join(repoRoot, product.lifecycleConfigPath);
        if (!existsSync(configPath)) {
          warnings.push(`Product ${productId}: lifecycle config file missing at ${product.lifecycleConfigPath}`);
        }
      } else {
        warnings.push(`Product ${productId}: no lifecycleConfigPath declared while status is ${lifecycleStatus}`);
      }
    }
  }

  return { errors, warnings };
}

function main() {
  const registry = loadRegistry();
  const lifecycleProfiles = loadLifecycleProfiles();
  const toolchains = loadToolchainRegistry();
  const excludedProducts = loadLifecycleExclusions();
  const { errors, warnings } = checkProductLifecycleContracts(registry, lifecycleProfiles, toolchains, excludedProducts);

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
}

try {
  main();
} catch (error) {
  console.error(`Lifecycle contract check failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
