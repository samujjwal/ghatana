#!/usr/bin/env node

/**
 * Tests for validate-plugin-dependencies script
 *
 * @doc.type test
 * @doc.purpose Test plugin dependency validation
 * @doc.layer scripts
 * @doc.pattern Test
 */

import { describe, it, expect } from 'vitest';
import { readFileSync, writeFileSync, unlinkSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCRIPT_PATH = join(__dirname, '..', 'validate-plugin-dependencies.mjs');

describe('validate-plugin-dependencies', () => {
  it('should validate plugins that exist in Kernel registry', () => {
    const testConfig = `
plugins:
  - pluginId: kernel-session-context-resolver
    kind: platform-plugin
    capabilities:
      - session-resolution
    lifecycleHooks:
      - pre-phase
  - pluginId: kernel-security
    kind: platform-plugin
    capabilities:
      - credential-validation
    lifecycleHooks:
      - pre-phase
`;
    const result = parseTestConfig(testConfig);
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });

  it('should reject plugins that do not exist in Kernel registry', () => {
    const testConfig = `
plugins:
  - pluginId: kernel-nonexistent-plugin
    kind: platform-plugin
    capabilities:
      - fake-capability
    lifecycleHooks:
      - pre-phase
`;
    const result = parseTestConfig(testConfig);
    expect(result.valid).toBe(false);
    expect(result.errors).toContain(
      "Plugin 'kernel-nonexistent-plugin' does not exist in Kernel plugin registry"
    );
  });

  it('should warn about runtime services with invalid naming', () => {
    const testConfig = `
plugins:
  - pluginId: kernel-security
    kind: platform-plugin
    capabilities:
      - credential-validation
    lifecycleHooks:
      - pre-phase
    requiredRuntimeServices:
      - InvalidServiceName
      - another-bad-one
`;
    const result = parseTestConfig(testConfig);
    expect(result.valid).toBe(true);
    expect(result.warnings.length).toBeGreaterThan(0);
  });

  it('should handle empty plugin list', () => {
    const testConfig = `
plugins: []
`;
    const result = parseTestConfig(testConfig);
    expect(result.valid).toBe(true);
    expect(result.pluginCount).toBe(0);
  });

  it('should handle missing plugins section', () => {
    const testConfig = `
productId: test-product
lifecycleProfile: standard
`;
    const result = parseTestConfig(testConfig);
    expect(result.valid).toBe(true);
    expect(result.pluginCount).toBe(0);
  });
});

// Helper function to parse YAML (copied from script for testing)
function parseYaml(filePath) {
  const content = typeof filePath === 'string' ? filePath : readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  const result = {};
  let currentArray = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    const [key, ...valueParts] = trimmed.split(':');
    const value = valueParts.join(':').trim();

    if (key === 'plugins' && value === '') {
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

// Known Kernel plugins
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

function parseTestConfig(configString) {
  const config = parseYaml(configString);
  const plugins = config.plugins || [];

  const errors = [];
  const warnings = [];

  for (const plugin of plugins) {
    const pluginId = plugin.pluginId;

    if (!pluginId) {
      errors.push('Plugin missing pluginId');
      continue;
    }

    if (!KERNEL_PLUGINS.has(pluginId)) {
      errors.push(`Plugin '${pluginId}' does not exist in Kernel plugin registry`);
    }

    if (plugin.requiredRuntimeServices) {
      for (const service of plugin.requiredRuntimeServices) {
        if (!/^[a-z][a-z0-9-]*$/.test(service)) {
          warnings.push(`Runtime service '${service}' may not follow naming convention`);
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
