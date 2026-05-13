#!/usr/bin/env node

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

function loadAdapterRegistry() {
  return JSON.parse(readFileSync(adapterRegistryPath, 'utf8')).adapters;
}

function loadRegistry() {
  return JSON.parse(readFileSync(registryPath, 'utf8')).registry;
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

function checkToolchainAdapterContracts(adapterRegistry, usedAdapters) {
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

    // Only check implementation file if adapter is used by a product
    if (adapter.implementation && usedAdapters.has(adapterId)) {
      const implPath = join(repoRoot, adapter.implementation);
      if (!existsSync(implPath)) {
        errors.push(`Adapter ${adapterId}: implementation file not found at ${adapter.implementation}`);
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

  return { errors, warnings };
}

function main() {
  const adapterRegistry = loadAdapterRegistry();
  const registry = loadRegistry();
  const usedAdapters = getUsedAdapters(registry);
  const { errors, warnings } = checkToolchainAdapterContracts(adapterRegistry, usedAdapters);

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
