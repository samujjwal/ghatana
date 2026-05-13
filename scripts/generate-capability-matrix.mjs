#!/usr/bin/env node

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import path, { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  loadProductManifest,
  parseRegistry,
} from '../platform/typescript/product-manifest-contracts/index.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');
const outputPath = join(repoRoot, 'docs/generated/CAPABILITY_MATRIX.md');

function activeManifestProducts(canonicalRegistry) {
  return Object.entries(canonicalRegistry.registry)
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([, product]) => product.kind === 'business-product')
    .filter(([, product]) => product.conformance?.manifest === true)
    .filter(([, product]) => product.manifestPath && product.manifestFormat)
    .sort(([leftId], [rightId]) => leftId.localeCompare(rightId));
}

function loadManifest(productId, product) {
  const manifestPath = join(repoRoot, product.manifestPath);
  const manifest = loadProductManifest(manifestPath, product.manifestFormat);
  if (!manifest || typeof manifest !== 'object') {
    throw new Error(`${productId} manifest did not parse to an object`);
  }

  return manifest;
}

function bridgeEvidence(product) {
  const adapters = product.conformance?.bridgeAdapters ?? [];
  return adapters.flatMap((adapter) => {
    const tests = adapter.tests ?? [];
    if (tests.length === 0) {
      return [adapter.file];
    }
    return tests.map((test) => `${adapter.file} -> ${test.file}`);
  });
}

function conformanceStatus(product) {
  const conformance = product.conformance ?? {};
  const enabled = Object.entries(conformance)
    .filter(([key, value]) => key !== 'bridgeAdapters' && value === true)
    .map(([key]) => key)
    .sort();

  return enabled.length === 0 ? 'No enabled conformance gates' : enabled.join(', ');
}

function buildProductCapabilityRows(canonicalRegistry) {
  const rows = [];

  for (const [productId, product] of activeManifestProducts(canonicalRegistry)) {
    const manifest = loadManifest(productId, product);
    const capabilities = [...manifest.kernelCapabilitiesConsumed].sort();
    const adapters = [
      ...(manifest.bridgesConsumed ?? []),
      ...(manifest.runtimeServices ?? []),
      ...bridgeEvidence(product),
    ].sort();
    const tests = [
      product.manifestPath,
      product.buildFile,
      ...(product.conformance?.observability ? ['config/observability/product-observability-flows.json'] : []),
      ...bridgeEvidence(product).map((evidence) => evidence.split(' -> ').at(-1)),
    ].filter(Boolean).sort();

    for (const capability of capabilities) {
      rows.push({
        productId,
        capability,
        adapters: adapters.length === 0 ? 'Manifest only' : adapters.join('<br>'),
        tests: [...new Set(tests)].join('<br>'),
        status: conformanceStatus(product),
      });
    }
  }

  return rows;
}

function markdownTable(headers, rows) {
  return [
    `| ${headers.join(' | ')} |`,
    `| ${headers.map(() => '---').join(' | ')} |`,
    ...rows.map((row) => `| ${row.join(' | ')} |`),
  ].join('\n');
}

function renderCapabilityMatrix({ canonicalRegistry, capabilityRegistry }) {
  const capabilityRows = Object.entries(capabilityRegistry.kernelCapabilities ?? {})
    .sort(([leftId], [rightId]) => leftId.localeCompare(rightId))
    .map(([capabilityId, capability]) => [
      `\`${capabilityId}\``,
      capability.description,
    ]);

  const pluginRows = Object.entries(capabilityRegistry.plugins ?? {})
    .sort(([leftId], [rightId]) => leftId.localeCompare(rightId))
    .map(([pluginId, plugin]) => [
      `\`${pluginId}\``,
      plugin.description,
    ]);

  const domainPackRows = Object.entries(capabilityRegistry.domainPacks ?? {})
    .sort(([leftId], [rightId]) => leftId.localeCompare(rightId))
    .map(([domainPackId, domainPack]) => [
      `\`${domainPackId}\``,
      domainPack.owner ?? domainPack.description ?? 'kernel',
    ]);

  const productRows = buildProductCapabilityRows(canonicalRegistry).map((row) => [
    `\`${row.productId}\``,
    `\`${row.capability}\``,
    row.adapters,
    row.tests,
    row.status,
  ]);

  return `${[
    '# Ghatana Platform Capability Matrix',
    '',
    '> Generated from `config/canonical-product-registry.json`, product manifests, and `config/kernel-product-capability-registry.json`.',
    '',
    '## Product Capability Consumption',
    '',
    markdownTable(['Product', 'Capability', 'Adapter / Runtime Evidence', 'Tests / Conformance Evidence', 'Conformance Status'], productRows),
    '',
    '## Kernel Capabilities',
    '',
    markdownTable(['Capability', 'Description'], capabilityRows),
    '',
    '## Platform Plugins',
    '',
    markdownTable(['Plugin', 'Description'], pluginRows),
    '',
    '## Product Domain Packs',
    '',
    markdownTable(['Domain Pack', 'Owner'], domainPackRows),
    '',
    '## Policy Vocabulary',
    '',
    `Canonical actions: \`${(capabilityRegistry.policyActions?.canonical ?? []).join('`, `')}\`.`,
    '',
    'Product-specific actions must use a registered product namespace, for example `finance:settle` or `digital-marketing:launch`.',
    '',
  ].join('\n')}`;
}

function main() {
  const checkOnly = process.argv.includes('--check');
  const canonicalRegistry = parseRegistry(join(repoRoot, 'config/canonical-product-registry.json'));
  const capabilityRegistry = parseRegistry(join(repoRoot, 'config/kernel-product-capability-registry.json'));
  const nextContent = renderCapabilityMatrix({ canonicalRegistry, capabilityRegistry });
  const currentContent = existsSync(outputPath) ? readFileSync(outputPath, 'utf8') : '';

  if (checkOnly) {
    if (currentContent !== nextContent) {
      throw new Error('docs/generated/CAPABILITY_MATRIX.md is stale; run pnpm generate:capability-matrix');
    }
    console.log('Capability matrix is up to date.');
    return;
  }

  writeFileSync(outputPath, nextContent);
  console.log(`Generated ${path.relative(repoRoot, outputPath)}`);
}

try {
  main();
} catch (error) {
  console.error(`Capability matrix generation failed: ${error.message}`);
  process.exit(1);
}
