import assert from 'node:assert/strict';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { tmpdir } from 'node:os';
import test from 'node:test';

import { checkProductRegistryDrift } from '../check-product-registry-drift.mjs';

function createFixtureRoot(registryOverrides = {}, extraFiles = {}) {
  const root = path.join(tmpdir(), `drift-test-${Date.now()}-${Math.random().toString(36).slice(2)}`);
  mkdirSync(root, { recursive: true });

  const defaultRegistry = {
    version: '1.0.0',
    registry: {
      'digital-marketing': {
        id: 'digital-marketing',
        name: 'Digital Marketing',
        kind: 'business-product',
        lifecycleProfile: 'standard-web-api-product',
        lifecycleStatus: 'enabled',
        lifecycleMigration: { status: 'ready' },
        lifecycleConfigPath: 'products/digital-marketing/kernel-product.yaml',
        lifecycle: { enabled: true },
        surfaces: [
          { type: 'backend-api', path: 'products/digital-marketing/dm-api', implementationStatus: 'implemented' },
          { type: 'web', path: 'products/digital-marketing/ui', implementationStatus: 'implemented' },
        ],
        toolchain: { adapters: { 'backend-api': 'gradle-java-service', web: 'pnpm-vite-react' } },
        artifacts: {
          'backend-api': { type: 'jvm-service', required: true },
          web: { type: 'static-web-bundle', required: true },
        },
        deployment: { targets: ['compose-local'], healthChecks: ['standard-http'] },
        pnpmPackages: ['products/digital-marketing/ui'],
        gradleModules: [],
      },
    },
  };

  const registry = { ...defaultRegistry, registry: { ...defaultRegistry.registry, ...registryOverrides } };

  mkdirSync(path.join(root, 'config'), { recursive: true });
  writeFileSync(path.join(root, 'config/canonical-product-registry.json'), JSON.stringify(registry, null, 2));
  writeFileSync(path.join(root, 'pnpm-workspace.yaml'), 'packages:\n  - "products/digital-marketing/ui"\n');
  writeFileSync(path.join(root, 'settings.gradle.kts'), '');

  for (const [filePath, content] of Object.entries(extraFiles)) {
    const fullPath = path.join(root, filePath);
    mkdirSync(path.dirname(fullPath), { recursive: true });
    writeFileSync(fullPath, content);
  }

  return root;
}

function cleanFixture(root) {
  try { rmSync(root, { recursive: true, force: true }); } catch { /* ignore */ }
}

test('Digital Marketing enabled passes', () => {
  const root = createFixtureRoot();
  try {
    const violations = checkProductRegistryDrift({ repoRoot: root, registryPath: 'config/canonical-product-registry.json' });
    const dmViolations = violations.filter((v) => v.startsWith('digital-marketing'));
    assert.equal(dmViolations.length, 0, `expected no Digital Marketing violations, got: ${dmViolations.join('; ')}`);
  } finally {
    cleanFixture(root);
  }
});

test('enabled product missing toolchain fails', () => {
  const root = createFixtureRoot({
    'broken-product': {
      id: 'broken-product',
      name: 'Broken Product',
      kind: 'business-product',
      lifecycleStatus: 'enabled',
      lifecycleMigration: { status: 'ready' },
      lifecycleConfigPath: 'products/broken-product/kernel-product.yaml',
      lifecycle: { enabled: true },
      surfaces: [{ type: 'backend-api', implementationStatus: 'implemented' }],
      toolchain: { adapters: {} },
      artifacts: { 'backend-api': { type: 'jvm-service', required: true } },
      deployment: { targets: ['compose-local'], healthChecks: ['standard-http'] },
      pnpmPackages: [],
      gradleModules: [],
    },
  });

  try {
    const violations = checkProductRegistryDrift({ repoRoot: root, registryPath: 'config/canonical-product-registry.json' });
    const toolchainViolations = violations.filter((v) => v.includes('toolchain'));
    assert(toolchainViolations.length > 0, 'expected toolchain violation for missing adapter mapping');
  } finally {
    cleanFixture(root);
  }
});

test('disabled product missing reason code fails', () => {
  const root = createFixtureRoot({
    phr: {
      id: 'phr',
      name: 'PHR',
      kind: 'business-product',
      lifecycleStatus: 'planned',
      lifecycle: { enabled: false },
      lifecycleReadiness: { reasonCodes: [] },
      surfaces: [],
      pnpmPackages: [],
      gradleModules: [],
    },
  });

  try {
    const violations = checkProductRegistryDrift({ repoRoot: root, registryPath: 'config/canonical-product-registry.json' });
    const reasonViolations = violations.filter((v) => v.includes('reasonCodes'));
    assert(reasonViolations.length > 0, 'expected violation for missing reason codes on disabled product');
  } finally {
    cleanFixture(root);
  }
});

test('stale generated Gradle include fails', () => {
  const root = createFixtureRoot({
    'digital-marketing': {
      id: 'digital-marketing',
      name: 'Digital Marketing',
      kind: 'business-product',
      lifecycleStatus: 'enabled',
      lifecycleMigration: { status: 'ready' },
      lifecycleConfigPath: 'products/digital-marketing/kernel-product.yaml',
      lifecycle: { enabled: true },
      surfaces: [
        { type: 'backend-api', implementationStatus: 'implemented' },
        { type: 'web', implementationStatus: 'implemented' },
      ],
      toolchain: { adapters: { 'backend-api': 'gradle-java-service', web: 'pnpm-vite-react' } },
      artifacts: {
        'backend-api': { type: 'jvm-service', required: true },
        web: { type: 'static-web-bundle', required: true },
      },
      deployment: { targets: ['compose-local'], healthChecks: ['standard-http'] },
      pnpmPackages: ['products/digital-marketing/ui'],
      gradleModules: [':products:digital-marketing:dm-api'],
    },
  });

  try {
    const violations = checkProductRegistryDrift({ repoRoot: root, registryPath: 'config/canonical-product-registry.json' });
    const gradleViolations = violations.filter((v) => v.includes('Gradle module'));
    assert(gradleViolations.length > 0, 'expected Gradle module violation for missing generated include');
  } finally {
    cleanFixture(root);
  }
});

test('platform-provider enabled without manifest fails', () => {
  const root = createFixtureRoot({
    'data-cloud': {
      id: 'data-cloud',
      name: 'Data Cloud',
      kind: 'platform-provider',
      manifestPath: null,
      lifecycleStatus: 'enabled',
      lifecycleMigration: { status: 'ready' },
      lifecycleConfigPath: 'products/data-cloud/kernel-product.yaml',
      lifecycle: { enabled: true },
      surfaces: [{ type: 'backend-api', implementationStatus: 'implemented' }],
      toolchain: { adapters: { 'backend-api': 'gradle-java-service' } },
      artifacts: { 'backend-api': { type: 'jvm-service', required: true } },
      deployment: { targets: ['compose-local'], healthChecks: ['standard-http'] },
      pnpmPackages: [],
      gradleModules: [],
    },
  });

  try {
    const violations = checkProductRegistryDrift({ repoRoot: root, registryPath: 'config/canonical-product-registry.json' });
    const providerViolations = violations.filter((v) => v.includes('platform-provider'));
    assert(providerViolations.length > 0, 'expected violation for platform-provider enabled without manifest');
  } finally {
    cleanFixture(root);
  }
});

test('PHR enabled fails as regression guard', () => {
  const root = createFixtureRoot({
    phr: {
      id: 'phr',
      name: 'PHR',
      kind: 'business-product',
      lifecycleStatus: 'enabled',
      lifecycleMigration: { status: 'ready' },
      lifecycleConfigPath: 'products/phr/kernel-product.yaml',
      lifecycle: { enabled: true },
      surfaces: [{ type: 'backend-api', implementationStatus: 'implemented' }],
      toolchain: { adapters: { 'backend-api': 'gradle-java-service' } },
      artifacts: { 'backend-api': { type: 'jvm-service', required: true } },
      deployment: { targets: ['compose-local'], healthChecks: ['standard-http'] },
      pnpmPackages: [],
      gradleModules: [],
    },
  });

  try {
    const violations = checkProductRegistryDrift({ repoRoot: root, registryPath: 'config/canonical-product-registry.json' });
    const phrViolations = violations.filter((v) => v.startsWith('phr:'));
    assert(phrViolations.length > 0, 'expected violation preventing PHR enablement');
  } finally {
    cleanFixture(root);
  }
});
