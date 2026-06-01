#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadProductManifest } from '../platform/typescript/product-manifest-contracts/index.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const productId = 'phr';

const paths = {
  canonicalRegistry: join(repoRoot, 'config', 'canonical-product-registry.json'),
  capabilityRegistry: join(repoRoot, 'config', 'kernel-product-capability-registry.json'),
  kernelPluginRegistry: join(repoRoot, 'config', 'kernel-plugin-registry.json'),
  domainManifest: join(repoRoot, 'products', productId, 'domain-pack-manifest.yaml'),
  bindingManifest: join(repoRoot, 'products', productId, 'plugin-bindings', 'phr-plugin-bindings.yaml'),
  buildFile: join(repoRoot, 'products', productId, 'build.gradle.kts'),
};

const requiredPhrPlugins = Object.freeze([
  'plugin-audit-trail',
  'plugin-compliance',
  'plugin-consent',
  'plugin-fraud-detection',
  'plugin-human-approval',
  'plugin-ledger',
  'plugin-risk-management',
]);
const requiredPhrKernelPlugins = Object.freeze([
  'security',
  'policy',
  'privacy',
  'mobile-privacy',
  'audit',
  'observability',
  'fhir-hl7',
  'document-ocr',
  'localization-accessibility',
]);

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function readYaml(filePath) {
  const parsed = loadProductManifest(filePath, 'yaml');
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${filePath} must parse to an object`);
  }
  return parsed;
}

function sorted(values) {
  return [...values].sort((left, right) => left.localeCompare(right));
}

function setEquals(left, right) {
  if (left.size !== right.size) {
    return false;
  }
  for (const value of left) {
    if (!right.has(value)) {
      return false;
    }
  }
  return true;
}

function collectGradlePluginDependencies(buildText) {
  const dependencies = new Set();
  const pattern = /^\s*(?:api|implementation)\(project\(":platform-plugins:(plugin-[^"]+)"\)\)/gm;
  for (const match of buildText.matchAll(pattern)) {
    dependencies.add(match[1]);
  }
  return dependencies;
}

export function validatePhrPluginManifest({
  canonicalRegistry,
  capabilityRegistry,
  kernelPluginRegistry,
  domainManifest,
  bindingManifest,
  buildText,
}) {
  const errors = [];
  const product = canonicalRegistry.registry?.[productId];

  if (!product) {
    errors.push('canonical product registry must declare PHR as a Kernel product');
  } else {
    if (product.id !== productId) {
      errors.push(`canonical product id must be '${productId}'`);
    }
    if (product.kind !== 'business-product') {
      errors.push("PHR canonical product kind must be 'business-product'");
    }
    if (product.conformance?.manifest !== true) {
      errors.push('PHR canonical product must require manifest conformance');
    }
    if (product.manifestPath !== 'products/phr/domain-pack-manifest.yaml') {
      errors.push('PHR canonical product must point at products/phr/domain-pack-manifest.yaml');
    }
    if (product.buildFile !== 'products/phr/build.gradle.kts') {
      errors.push('PHR canonical product must declare products/phr/build.gradle.kts as buildFile');
    }
    if (product.lifecycleConfigPath !== 'products/phr/kernel-product.yaml') {
      errors.push('PHR canonical product must declare products/phr/kernel-product.yaml as lifecycleConfigPath');
    }
  }

  if (domainManifest.product !== productId || domainManifest.id !== productId) {
    errors.push('PHR domain manifest id and product must both be phr');
  }

  const consumedPlugins = new Set(domainManifest.pluginsConsumed ?? []);
  const missingRequiredPlugins = requiredPhrPlugins.filter((plugin) => !consumedPlugins.has(plugin));
  for (const plugin of missingRequiredPlugins) {
    errors.push(`PHR domain manifest must consume required Kernel plugin '${plugin}'`);
  }

  for (const plugin of consumedPlugins) {
    if (!capabilityRegistry.plugins?.[plugin]) {
      errors.push(`PHR domain manifest consumes unknown Kernel plugin '${plugin}'`);
    }
  }

  const consumedKernelPlugins = new Set(domainManifest.kernelPluginsConsumed ?? []);
  const missingRequiredKernelPlugins = requiredPhrKernelPlugins.filter((plugin) => !consumedKernelPlugins.has(plugin));
  for (const plugin of missingRequiredKernelPlugins) {
    errors.push(`PHR domain manifest must consume required Kernel plugin contract '${plugin}'`);
  }

  for (const plugin of consumedKernelPlugins) {
    if (!kernelPluginRegistry.plugins?.[plugin]) {
      errors.push(`PHR domain manifest consumes unknown Kernel plugin contract '${plugin}'`);
    }
  }

  if (bindingManifest.product !== productId) {
    errors.push('PHR plugin binding manifest product must be phr');
  }
  if (typeof bindingManifest.version !== 'string' || !/^\d+\.\d+\.\d+(?:[-+].+)?$/.test(bindingManifest.version)) {
    errors.push('PHR plugin binding manifest must declare a semver version');
  }
  if (!Array.isArray(bindingManifest.bindings) || bindingManifest.bindings.length === 0) {
    errors.push('PHR plugin binding manifest must declare at least one binding');
  }

  const boundCanonicalPlugins = new Set();
  const runtimePluginIds = new Set();
  for (const [index, binding] of (bindingManifest.bindings ?? []).entries()) {
    const location = `PHR plugin binding at index ${index}`;
    if (!binding || typeof binding !== 'object' || Array.isArray(binding)) {
      errors.push(`${location} must be an object`);
      continue;
    }
    if (typeof binding.canonicalPluginId !== 'string') {
      errors.push(`${location} must declare canonicalPluginId`);
    } else {
      boundCanonicalPlugins.add(binding.canonicalPluginId);
      if (!capabilityRegistry.plugins?.[binding.canonicalPluginId]) {
        errors.push(`${location} references unknown canonicalPluginId '${binding.canonicalPluginId}'`);
      }
    }
    if (typeof binding.pluginId !== 'string' || binding.pluginId.length === 0) {
      errors.push(`${location} must declare runtime pluginId`);
    } else if (runtimePluginIds.has(binding.pluginId)) {
      errors.push(`PHR plugin binding manifest declares duplicate runtime pluginId '${binding.pluginId}'`);
    } else {
      runtimePluginIds.add(binding.pluginId);
    }
    if (binding.enabled !== true) {
      errors.push(`${location} must be enabled for production PHR plugin composition`);
    }
    if (typeof binding.version !== 'string' || !/^>=\d+\.\d+\.\d+(?:[-+].+)?$/.test(binding.version)) {
      errors.push(`${location} must declare a >= semver version range`);
    }
    if (!binding.config || typeof binding.config !== 'object' || Array.isArray(binding.config)) {
      errors.push(`${location} must provide product-scoped config`);
    }
  }

  if (!setEquals(consumedPlugins, boundCanonicalPlugins)) {
    errors.push(
      `PHR pluginsConsumed must exactly match plugin binding canonicalPluginId values. consumed=[${sorted(consumedPlugins).join(', ')}], bound=[${sorted(boundCanonicalPlugins).join(', ')}]`,
    );
  }

  const buildPlugins = collectGradlePluginDependencies(buildText);
  if (!setEquals(consumedPlugins, buildPlugins)) {
    errors.push(
      `PHR build platform plugin dependencies must exactly match pluginsConsumed. consumed=[${sorted(consumedPlugins).join(', ')}], build=[${sorted(buildPlugins).join(', ')}]`,
    );
  }

  return errors;
}

function main() {
  for (const filePath of Object.values(paths)) {
    if (!existsSync(filePath)) {
      console.error(`PHR plugin manifest validation failed: missing ${filePath}`);
      process.exit(1);
    }
  }

  const errors = validatePhrPluginManifest({
    canonicalRegistry: readJson(paths.canonicalRegistry),
    capabilityRegistry: readJson(paths.capabilityRegistry),
    kernelPluginRegistry: readJson(paths.kernelPluginRegistry),
    domainManifest: readYaml(paths.domainManifest),
    bindingManifest: readYaml(paths.bindingManifest),
    buildText: readFileSync(paths.buildFile, 'utf8'),
  });

  if (errors.length > 0) {
    console.error(`PHR plugin manifest validation failed with ${errors.length} violation(s):`);
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log(
    `PHR plugin manifest validation passed for ${requiredPhrPlugins.length} platform plugins and ${requiredPhrKernelPlugins.length} Kernel plugin contracts.`,
  );
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}
