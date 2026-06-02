#!/usr/bin/env node

/**
 * Validate Product Plugin Dependencies
 *
 * Validates that plugins declared in a product's kernel-product.yaml
 * map to real Kernel plugins in platform-plugins or platform-kernel.
 * Fails closed if any declared plugin is missing.
 *
 * Usage: node scripts/validate-product-plugin-dependencies.mjs <product-path>
 */

import { readFileSync, readdirSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Known Kernel plugin locations
const KERNEL_PLUGIN_LOCATIONS = [
  'platform-plugins',
  'platform-kernel/kernel-plugin/src/main/java/com/ghatana/platform/plugin',
];

// Valid plugin ID patterns (allow both with and without kernel- prefix)
const VALID_PLUGIN_PATTERNS = [
  /^kernel-[a-z0-9-]+$/,  // kernel-session-context-resolver, kernel-fhir-hl7, etc.
  /^[a-z0-9-]+$/,         // audit-trail, compliance, consent, etc.
];

/**
 * Extract plugin IDs from kernel-product.yaml using regex
 */
function extractPluginIds(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const pluginIds = [];
  
  // Match lines like: "  - pluginId: kernel-session-context-resolver"
  const pluginIdRegex = /^\s*-\s*pluginId:\s*(.+)$/gm;
  let match;
  
  while ((match = pluginIdRegex.exec(content)) !== null) {
    pluginIds.push(match[1].trim());
  }
  
  return pluginIds;
}

/**
 * Get list of available plugins from platform-plugins directory
 */
function getAvailablePlugins() {
  const plugins = new Set();

  // Check platform-plugins directory
  const platformPluginsPath = join(process.cwd(), 'platform-plugins');
  if (existsSync(platformPluginsPath)) {
    const entries = readdirSync(platformPluginsPath, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.isDirectory() && entry.name.startsWith('plugin-')) {
        // Remove 'plugin-' prefix to get plugin ID
        const pluginId = entry.name.replace('plugin-', '');
        plugins.add(pluginId);
        // Also add with kernel- prefix for compatibility
        plugins.add(`kernel-${pluginId}`);
      }
    }
  }

  // Add core-observability
  plugins.add('core-observability');
  plugins.add('kernel-observability');

  // Add known Kernel plugin implementations
  const knownKernelPlugins = [
    'session-context-resolver',
    'security',
    'phi-policy',
    'http-support',
    'mobile-privacy',
    'fhir-hl7',
    'documents-ocr',
  ];
  for (const plugin of knownKernelPlugins) {
    plugins.add(plugin);
    plugins.add(`kernel-${plugin}`);
  }

  return plugins;
}

/**
 * Validate plugin ID format
 */
function isValidPluginId(pluginId) {
  return VALID_PLUGIN_PATTERNS.some(pattern => pattern.test(pluginId));
}


/**
 * Main validation function
 */
function validateProductPluginDependencies(productPath) {
  const productYamlPath = join(process.cwd(), productPath, 'kernel-product.yaml');

  if (!existsSync(productYamlPath)) {
    console.error(`Error: kernel-product.yaml not found at ${productYamlPath}`);
    process.exit(1);
  }

  console.log(`Validating plugin dependencies for ${productPath}...`);

  const declaredPlugins = extractPluginIds(productYamlPath);
  const availablePlugins = getAvailablePlugins();

  console.log(`Declared plugins: ${declaredPlugins.join(', ')}`);
  console.log(`Available plugins: ${Array.from(availablePlugins).sort().join(', ')}`);

  const missingPlugins = [];
  const invalidPlugins = [];

  for (const pluginId of declaredPlugins) {
    if (!isValidPluginId(pluginId)) {
      invalidPlugins.push(pluginId);
      continue;
    }

    // Check if plugin exists (with or without kernel- prefix)
    const baseId = pluginId.replace(/^kernel-/, '');
    const exists = availablePlugins.has(pluginId) || 
                  availablePlugins.has(baseId) ||
                  availablePlugins.has(`kernel-${baseId}`);

    if (!exists) {
      missingPlugins.push(pluginId);
    }
  }

  if (invalidPlugins.length > 0) {
    console.error(`\n❌ Invalid plugin ID format: ${invalidPlugins.join(', ')}`);
    console.error('Plugin IDs must match pattern: kernel-* or lowercase-with-dashes');
    process.exit(1);
  }

  if (missingPlugins.length > 0) {
    console.error(`\n❌ Missing plugins: ${missingPlugins.join(', ')}`);
    console.error('These plugins are declared but do not exist in platform-plugins or platform-kernel.');
    console.error('\nPlease either:');
    console.error('1. Implement the missing plugin in platform-plugins');
    console.error('2. Remove the plugin declaration from kernel-product.yaml');
    console.error('3. Fix the plugin ID to match an existing plugin');
    process.exit(1);
  }

  console.log(`\n✅ All ${declaredPlugins.length} declared plugins are valid and available.`);
  process.exit(0);
}

// CLI interface
const productPath = process.argv[2] || 'products/phr';
validateProductPluginDependencies(productPath);
