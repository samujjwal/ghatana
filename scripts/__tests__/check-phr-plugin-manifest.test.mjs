import assert from 'node:assert/strict';
import test from 'node:test';

import { validatePhrPluginManifest } from '../check-phr-plugin-manifest.mjs';

const requiredPlugins = [
  'plugin-audit-trail',
  'plugin-compliance',
  'plugin-consent',
  'plugin-fraud-detection',
  'plugin-human-approval',
  'plugin-ledger',
  'plugin-risk-management',
];

function fixture(overrides = {}) {
  const canonicalRegistry = {
    registry: {
      phr: {
        id: 'phr',
        kind: 'business-product',
        conformance: { manifest: true },
        manifestPath: 'products/phr/domain-pack-manifest.yaml',
        buildFile: 'products/phr/build.gradle.kts',
        lifecycleConfigPath: 'products/phr/kernel-product.yaml',
      },
    },
  };
  const capabilityRegistry = {
    plugins: Object.fromEntries(requiredPlugins.map((plugin) => [plugin, { description: plugin }])),
  };
  const domainManifest = {
    id: 'phr',
    product: 'phr',
    pluginsConsumed: requiredPlugins,
  };
  const bindingManifest = {
    product: 'phr',
    version: '1.0.0',
    bindings: requiredPlugins.map((plugin) => ({
      canonicalPluginId: plugin,
      pluginId: plugin.replace(/^plugin-/, '').concat('-plugin'),
      version: '>=1.0.0',
      enabled: true,
      config: { mode: 'strict' },
    })),
  };
  const buildText = requiredPlugins
    .map((plugin) => `    api(project(":platform-plugins:${plugin}"))`)
    .join('\n');

  return {
    canonicalRegistry,
    capabilityRegistry,
    domainManifest,
    bindingManifest,
    buildText,
    ...overrides,
  };
}

test('PHR plugin manifest validation passes when canonical, binding, and build declarations align', () => {
  const errors = validatePhrPluginManifest(fixture());

  assert.deepEqual(errors, []);
});

test('PHR plugin manifest validation fails when a required plugin binding is missing', () => {
  const bindingManifest = fixture().bindingManifest;
  const errors = validatePhrPluginManifest(fixture({
    bindingManifest: {
      ...bindingManifest,
      bindings: bindingManifest.bindings.filter((binding) => binding.canonicalPluginId !== 'plugin-consent'),
    },
  }));

  assert(errors.some((error) => error.includes('pluginsConsumed must exactly match plugin binding canonicalPluginId values')));
});

test('PHR plugin manifest validation fails when build dependencies drift from pluginsConsumed', () => {
  const errors = validatePhrPluginManifest(fixture({
    buildText: '    api(project(":platform-plugins:plugin-consent"))',
  }));

  assert(errors.some((error) => error.includes('build platform plugin dependencies must exactly match pluginsConsumed')));
});
