#!/usr/bin/env node
// Authoritative Source: docs/kernel/PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md

/**
 * Check toolchain adapter contracts
 *
 * Validates that:
 * - Toolchain adapters are registered
 * - Adapters implement required methods
 * - Adapter configurations are valid
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const adapterRegistryPath = join(repoRoot, 'config/toolchain-adapter-registry.json');
const registryPath = join(repoRoot, 'config/canonical-product-registry.json');
const lifecycleProfilesPath = join(repoRoot, 'config/product-lifecycle-profiles.json');

function loadAdapterRegistry() {
  return JSON.parse(readFileSync(adapterRegistryPath, 'utf8')).adapters;
}

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
}

function loadLifecycleProfiles() {
  return JSON.parse(readFileSync(lifecycleProfilesPath, 'utf8')).profiles ?? {};
}

function getUsedAdapters(registry) {
  const usedAdapters = new Set();
  for (const product of Object.values(registry)) {
    if (product.lifecycle?.toolchain) {
      for (const adapterId of Object.values(product.lifecycle.toolchain)) {
        usedAdapters.add(adapterId);
      }
    }
    if (product.toolchain?.adapters) {
      for (const adapterId of Object.values(product.toolchain.adapters)) {
        usedAdapters.add(adapterId);
      }
    }
  }
  return usedAdapters;
}

function getDefaultProfileAdapters(lifecycleProfiles) {
  const profileDefaults = [];
  for (const [profileId, profile] of Object.entries(lifecycleProfiles)) {
    for (const [slot, adapterId] of Object.entries(profile.defaultAdapters ?? {})) {
      profileDefaults.push({
        profileId,
        slot,
        adapterId,
        status: profile.status ?? 'experimental',
        safeForDefault: profile.safeForDefault === true,
      });
    }
  }
  return profileDefaults;
}

function checkToolchainAdapterContracts(adapterRegistry, usedAdapters, lifecycleProfiles) {
  const errors = [];
  const warnings = [];

  for (const [adapterId, adapter] of Object.entries(adapterRegistry)) {
    // Check required fields
    if (!adapter.kind) {
      errors.push(`Adapter ${adapterId}: missing kind field`);
    }

    if (!adapter.supportedPhases || adapter.supportedPhases.length === 0) {
      errors.push(`Adapter ${adapterId}: missing or empty supportedPhases`);
    }

    if (!adapter.supportedSurfaceTypes || adapter.supportedSurfaceTypes.length === 0) {
      errors.push(`Adapter ${adapterId}: missing or empty supportedSurfaceTypes`);
    }

    if (!adapter.status || !['implemented', 'partial', 'planned'].includes(adapter.status)) {
      errors.push(`Adapter ${adapterId}: status must be one of implemented, partial, planned`);
    }

    if (typeof adapter.safeForDefault !== 'boolean') {
      errors.push(`Adapter ${adapterId}: safeForDefault must be a boolean`);
    }

    if (!Array.isArray(adapter.tests)) {
      errors.push(`Adapter ${adapterId}: tests must be an array`);
    }

    if (typeof adapter.planningImplemented !== 'boolean') {
      errors.push(`Adapter ${adapterId}: planningImplemented must be a boolean`);
    }

    if (typeof adapter.executionImplemented !== 'boolean') {
      errors.push(`Adapter ${adapterId}: executionImplemented must be a boolean`);
    }

    if (typeof adapter.outputValidationImplemented !== 'boolean') {
      errors.push(`Adapter ${adapterId}: outputValidationImplemented must be a boolean`);
    }

    if (adapter.status === 'planned' && adapter.safeForDefault !== false) {
      errors.push(`Adapter ${adapterId}: planned adapters must set safeForDefault to false`);
    }

    if (adapter.status === 'planned' && (adapter.planningImplemented || adapter.executionImplemented || adapter.outputValidationImplemented)) {
      errors.push(`Adapter ${adapterId}: planned adapters must set planningImplemented/executionImplemented/outputValidationImplemented to false`);
    }

    if (adapter.status === 'implemented' && (!adapter.planningImplemented || !adapter.executionImplemented || !adapter.outputValidationImplemented)) {
      errors.push(`Adapter ${adapterId}: implemented adapters must set planningImplemented/executionImplemented/outputValidationImplemented to true`);
    }

    // Enforce: safeForDefault: true requires status: implemented + all three flags true
    if (adapter.safeForDefault === true) {
      if (adapter.status !== 'implemented') {
        errors.push(`Adapter ${adapterId}: safeForDefault: true requires status: implemented (currently: ${adapter.status})`);
      }
      if (!adapter.planningImplemented || !adapter.executionImplemented || !adapter.outputValidationImplemented) {
        errors.push(`Adapter ${adapterId}: safeForDefault: true requires planningImplemented, executionImplemented, and outputValidationImplemented to all be true`);
      }
    }

    const requiresImplementationFile = adapter.status === 'implemented' || adapter.status === 'partial' || usedAdapters.has(adapterId);
    if (requiresImplementationFile) {
      if (!adapter.implementation) {
        errors.push(`Adapter ${adapterId}: implementation path is required for ${adapter.status ?? 'used'} adapters`);
      }
    }

    if (adapter.implementation && requiresImplementationFile) {
      const implPath = join(repoRoot, adapter.implementation);
      if (!existsSync(implPath)) {
        errors.push(`Adapter ${adapterId}: implementation file not found at ${adapter.implementation}`);
      }
    }

    if (adapter.status === 'implemented') {
      if (!adapter.tests.length) {
        errors.push(`Adapter ${adapterId}: implemented adapters must declare executable tests`);
      }

      for (const testPath of adapter.tests) {
        if (!existsSync(join(repoRoot, testPath))) {
          errors.push(`Adapter ${adapterId}: declared test file not found at ${testPath}`);
        }
      }

      // Validate implementation is exported from kernel-toolchains index.ts
      if (adapter.implementation) {
        const indexPath = join(repoRoot, 'platform/typescript/kernel-toolchains/src/index.ts');
        if (existsSync(indexPath)) {
          const indexContent = readFileSync(indexPath, 'utf8');
          const implFile = adapter.implementation.replace(/.*src\/adapters\//, '').replace('.ts', '');
          if (!indexContent.includes(implFile)) {
            errors.push(`Adapter ${adapterId}: implementation "${implFile}" not exported from kernel-toolchains/src/index.ts`);
          }
        }
      }
    }

    // Validate phase definitions
    if (adapter.phaseDefinitions) {
      for (const [phase, definition] of Object.entries(adapter.phaseDefinitions)) {
        if (!definition.command || definition.command.length === 0) {
          errors.push(`Adapter ${adapterId}: phase ${phase} has empty command`);
        }
      }
    }
  }

  for (const [profileId, profile] of Object.entries(lifecycleProfiles)) {
    if (typeof profile.safeForDefault !== 'boolean') {
      errors.push(`Lifecycle profile ${profileId}: safeForDefault must be a boolean`);
    }

    if (profile.status === 'stable' && profile.safeForDefault !== true) {
      errors.push(`Lifecycle profile ${profileId}: stable profiles must set safeForDefault to true`);
    }

    if (profile.status === 'experimental' && profile.safeForDefault === true) {
      errors.push(`Lifecycle profile ${profileId}: experimental profiles cannot set safeForDefault to true`);
    }
  }

  for (const { profileId, slot, adapterId, safeForDefault } of getDefaultProfileAdapters(lifecycleProfiles)) {
    const adapter = adapterRegistry[adapterId];
    if (!adapter) {
      errors.push(`Lifecycle profile ${profileId}: default adapter ${adapterId} for ${slot} is not registered`);
      continue;
    }

    if (safeForDefault && adapter.safeForDefault !== true) {
      errors.push(`Lifecycle profile ${profileId}: safe default profile cannot default ${slot} to unsafe adapter ${adapterId}`);
    }
  }

  return { errors, warnings };
}

function main() {
  const adapterRegistry = loadAdapterRegistry();
  const registry = loadRegistry();
  const lifecycleProfiles = loadLifecycleProfiles();
  const usedAdapters = getUsedAdapters(registry);
  const { errors, warnings } = checkToolchainAdapterContracts(adapterRegistry, usedAdapters, lifecycleProfiles);

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

  console.log('All toolchain adapter contracts are valid');
  process.exit(0);
}

try {
  main();
} catch (error) {
  console.error(`Toolchain adapter contract check failed: ${error.message}`);
  process.exit(1);
}
