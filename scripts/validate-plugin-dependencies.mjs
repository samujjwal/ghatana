#!/usr/bin/env node

/**
 * Validate plugin dependencies - verifies that product plugin dependencies
 * map to real Kernel plugins that exist in the platform.
 *
 * @doc.type script
 * @doc.purpose Validate plugin dependencies against Kernel plugin registry
 * @doc.layer scripts
 * @doc.pattern Validation
 */

import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..');

// Known Kernel plugins that exist in the platform
const KERNEL_PLUGINS = new Set([
  'kernel-session-context-resolver',
  'kernel-security',
  'kernel-phi-policy',
  'kernel-observability',
  'kernel-http-support',
  'kernel-lifecycle-planner',
  'kernel-lifecycle-executor',
  'kernel-gate-resolver',
  'kernel-policy-facade',
  'kernel-audit-trail',
  'kernel-metrics',
  'kernel-tracing',
]);

/**
 * Parse YAML file (simple parser for our specific use case)
 */
function parseYaml(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  const result = {};
  let currentKey = null;
  let currentArray = null;
  let currentObject = null;
  let indentLevel = 0;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    const indent = line.search(/\S/);
    const [key, ...valueParts] = trimmed.split(':');
    const value = valueParts.join(':').trim();

    if (indent === 0) {
      currentKey = key;
      currentArray = null;
      currentObject = null;
      result[key] = value === '' ? {} : value;
    } else if (key === 'plugins' && value === '') {
      currentArray = [];
      result[key] = currentArray;
    } else if (currentArray && key === 'pluginId') {
      currentArray.push({ pluginId: value });
    } else if (currentArray && currentArray.length > 0) {
      const lastItem = currentArray[currentArray.length - 1];
      if (key === 'kind') lastItem.kind = value;
      else if (key === 'capabilities') lastItem.capabilities = [];
      else if (key === 'lifecycleHooks') lastItem.lifecycleHooks = [];
      else if (key === 'requiredRuntimeServices') lastItem.requiredRuntimeServices = [];
      else if (value.startsWith('-')) {
        const arrayValue = value.replace('-', '').trim();
        if (lastItem.capabilities) lastItem.capabilities.push(arrayValue);
        else if (lastItem.lifecycleHooks) lastItem.lifecycleHooks.push(arrayValue);
        else if (lastItem.requiredRuntimeServices) lastItem.requiredRuntimeServices.push(arrayValue);
      }
    }
  }

  return result;
}

/**
 * Validate plugin dependencies for a product
 */
function validateProductPluginDependencies(productConfigPath) {
  if (!existsSync(productConfigPath)) {
    return {
      valid: false,
      error: `Product config not found: ${productConfigPath}`,
    };
  }

  const config = parseYaml(productConfigPath);
  const plugins = config.plugins || [];

  const errors = [];
  const warnings = [];

  for (const plugin of plugins) {
    const pluginId = plugin.pluginId;

    if (!pluginId) {
      errors.push(`Plugin missing pluginId in ${productConfigPath}`);
      continue;
    }

    if (!KERNEL_PLUGINS.has(pluginId)) {
      errors.push(
        `Plugin '${pluginId}' declared in ${productConfigPath} does not exist in Kernel plugin registry`
      );
    }

    // Validate required runtime services
    if (plugin.requiredRuntimeServices) {
      for (const service of plugin.requiredRuntimeServices) {
        // Basic validation - services should be lowercase with hyphens
        if (!/^[a-z][a-z0-9-]*$/.test(service)) {
          warnings.push(
            `Runtime service '${service}' for plugin '${pluginId}' may not follow naming convention`
          );
        }
      }
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
    pluginCount: plugins.length,
  };
}

/**
 * Main validation function
 */
function main() {
  const productConfigs = [
    join(REPO_ROOT, 'products', 'phr', 'kernel-product.yaml'),
    join(REPO_ROOT, 'products', 'finance', 'kernel-product.yaml'),
    join(REPO_ROOT, 'products', 'flashit', 'kernel-product.yaml'),
    join(REPO_ROOT, 'products', 'data-cloud', 'kernel-product.yaml'),
    join(REPO_ROOT, 'products', 'yappc', 'kernel-product.yaml'),
  ];

  let totalErrors = 0;
  let totalWarnings = 0;
  let totalPlugins = 0;

  console.log('Validating plugin dependencies against Kernel plugin registry...\n');

  for (const configPath of productConfigs) {
    if (!existsSync(configPath)) {
      console.log(`⚠️  Skipping ${configPath} (not found)`);
      continue;
    }

    const result = validateProductPluginDependencies(configPath);
    const productName = configPath.split('\\').pop().split('/').pop().replace('kernel-product.yaml', '');

    totalPlugins += result.pluginCount || 0;
    totalErrors += result.errors.length;
    totalWarnings += result.warnings.length;

    if (result.valid) {
      console.log(`✅ ${productName}: ${result.pluginCount} plugins validated`);
    } else {
      console.log(`❌ ${productName}: ${result.errors.length} errors`);
      for (const error of result.errors) {
        console.log(`   - ${error}`);
      }
    }

    if (result.warnings.length > 0) {
      console.log(`⚠️  ${productName}: ${result.warnings.length} warnings`);
      for (const warning of result.warnings) {
        console.log(`   - ${warning}`);
      }
    }
  }

  console.log(`\nSummary: ${totalPlugins} plugins checked, ${totalErrors} errors, ${totalWarnings} warnings`);

  if (totalErrors > 0) {
    process.exit(1);
  }
}

main();
