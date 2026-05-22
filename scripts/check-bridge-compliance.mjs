#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { execSync, spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const registryPath = resolve(repoRoot, 'config/canonical-product-registry.json');
const registry = JSON.parse(readFileSync(registryPath, 'utf8')).registry;
const registryEntries = Object.values(registry);
const bridgeProducts = registryEntries.filter((entry) =>
  entry.kind === 'business-product' &&
  entry.metadata?.status === 'active' &&
  entry.conformance?.bridge === true,
);
const bridgeProductRoots = bridgeProducts.flatMap((entry) =>
  (entry.surfaces ?? []).map((surface) => surface.path.split('/').slice(0, 2).join('/')),
);
const bridgeFiles = execSync(
  "rg --files products | rg '(KernelAdapterImpl|Bridge.*Impl)\\.java$'",
  { cwd: repoRoot, encoding: 'utf8' },
)
  .trim()
  .split('\n')
  .filter(Boolean);
const auditedBridgeFiles = bridgeFiles.filter((file) =>
  bridgeProductRoots.some((product) => file.startsWith(`${product}/`)),
);
const expectedAuditedBridgeTests = new Map();
const failures = [];

for (const product of bridgeProducts) {
  const adapters = product.conformance?.bridgeAdapters;
  if (!Array.isArray(adapters) || adapters.length === 0) {
    failures.push(`${product.id}: bridge conformance is enabled but conformance.bridgeAdapters is empty`);
    continue;
  }

  for (const adapter of adapters) {
    if (!adapter.file || !Array.isArray(adapter.tests) || adapter.tests.length === 0) {
      failures.push(`${product.id}: bridge adapter contract must declare file and tests`);
      continue;
    }
    expectedAuditedBridgeTests.set(adapter.file, adapter.tests);
  }
}

for (const file of auditedBridgeFiles) {
  if (!expectedAuditedBridgeTests.has(file)) {
    failures.push(`${file}: audited bridge implementation is missing an explicit compliance-test contract entry`);
  }
}

for (const [bridgeFile, testContracts] of expectedAuditedBridgeTests.entries()) {
  if (!auditedBridgeFiles.includes(bridgeFile)) {
    failures.push(`${bridgeFile}: expected audited bridge implementation is missing`);
    continue;
  }

  for (const { file } of testContracts) {
    const testPath = resolve(repoRoot, file ?? '');
    if (!file || !existsSync(testPath)) {
      failures.push(`${file}: expected bridge compliance test file is missing`);
      continue;
    }

    const gradleTest = toGradleTest(file);
    if (!gradleTest) {
      failures.push(`${file}: bridge compliance tests must be Java tests under src/test/java`);
      continue;
    }

    console.log(`\n=== Bridge compliance test: ${gradleTest.className} ===`);
    const result = spawnSync(
      './gradlew',
      [`${gradleTest.module}:test`, '--tests', gradleTest.className, '--no-daemon'],
      {
        cwd: repoRoot,
        stdio: 'inherit',
        shell: process.platform === 'win32',
      },
    );

    if (result.error) {
      failures.push(`${file}: ${result.error.message}`);
      continue;
    }
    if (result.status !== 0) {
      failures.push(`${file}: bridge compliance test exited with status ${result.status}`);
    }
  }
}

if (failures.length > 0) {
  console.error('\nBridge compliance validation failed:');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log('\nBridge compliance validation passed (registry-declared executable tests).');

function toGradleTest(file) {
  const marker = '/src/test/java/';
  const markerIndex = file.indexOf(marker);
  if (markerIndex < 0 || !file.endsWith('.java')) {
    return null;
  }
  const module = `:${file.slice(0, markerIndex).split('/').join(':')}`;
  const className = file
    .slice(markerIndex + marker.length, -'.java'.length)
    .split('/')
    .join('.');
  return { module, className };
}
