#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('..', import.meta.url).pathname);
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
  { cwd: repoRoot, encoding: 'utf8' }
)
  .trim()
  .split('\n')
  .filter(Boolean);
const auditedBridgeFiles = bridgeFiles.filter(file => bridgeProductRoots.some(product => file.startsWith(`${product}/`)));
const expectedAuditedBridgeTests = new Map();

const violations = [];
const exemptMethodNames = new Set(['start', 'stop', 'started']);

for (const product of bridgeProducts) {
  const adapters = product.conformance?.bridgeAdapters;
  if (!Array.isArray(adapters) || adapters.length === 0) {
    violations.push(`${product.id}: bridge conformance is enabled but conformance.bridgeAdapters is empty`);
    continue;
  }

  for (const adapter of adapters) {
    if (!adapter.file || !Array.isArray(adapter.tests) || adapter.tests.length === 0) {
      violations.push(`${product.id}: bridge adapter contract must declare file and tests`);
      continue;
    }
    expectedAuditedBridgeTests.set(adapter.file, adapter.tests);
  }
}

for (const file of auditedBridgeFiles) {
  if (!expectedAuditedBridgeTests.has(file)) {
    violations.push(
      `${file}: audited bridge implementation is missing an explicit compliance-test contract entry`
    );
  }
}

for (const [bridgeFile, testContracts] of expectedAuditedBridgeTests.entries()) {
  if (!auditedBridgeFiles.includes(bridgeFile)) {
    violations.push(`${bridgeFile}: expected audited bridge implementation is missing`);
    continue;
  }

  for (const { file, requiredEvidence } of testContracts) {
    let content = '';
    try {
      content = readFileSync(resolve(repoRoot, file), 'utf8');
    } catch {
      violations.push(`${file}: expected bridge compliance test file is missing`);
      continue;
    }
    for (const token of requiredEvidence) {
      if (!content.includes(token)) {
        violations.push(`${file}: missing bridge compliance evidence token '${token}'`);
      }
    }
  }
}

for (const file of bridgeFiles) {
  const content = readFileSync(resolve(repoRoot, file), 'utf8');

  if (!content.includes('redact(')) {
    violations.push(`${file}: bridge adapter must redact logged metadata before logging sensitive context`);
  }

  const methods = [...content.matchAll(/public\s+Promise<[^>]+>\s+(\w+)\s*\(([\s\S]*?)\)\s*\{([\s\S]*?)\n    \}/g)];
  for (const method of methods) {
    const [, name, params, body] = method;
    if (exemptMethodNames.has(name)) {
      continue;
    }

    const hasContextParam = params.includes('BridgeContext') || params.includes('OperationContext');
    if (!hasContextParam) {
      continue;
    }

    if (!body.includes('requireStarted();')) {
      violations.push(`${file}#${name}: missing requireStarted()`);
    }

    if (hasContextParam && !body.includes('toBridgeContext()') && !params.includes('BridgeContext')) {
      violations.push(`${file}#${name}: must pass BridgeContext or convert product context via toBridgeContext()`);
    }

    if (!body.includes('checkAuthorized(')) {
      violations.push(`${file}#${name}: missing authorization check before bridge-sensitive work`);
    }

    if (/(attributes|metadata)/.test(body) && body.includes('LOG.') && !body.includes('redact(')) {
      violations.push(`${file}#${name}: metadata logging must be redacted`);
    }
  }
}

if (violations.length > 0) {
  console.error('Bridge compliance violations:');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Bridge compliance validation passed.');
